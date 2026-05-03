"""CLI 진입점 단위 테스트 — argparse, 카테고리 분기, 파이프라인 통계.

PR #74 의 test-writer 권고 (P0: cli.py 모듈 자체 테스트 0개) 해결.
HTTP/sitemap discover 는 mock 으로 격리하여 외부 호출 없이 검증.
"""

from __future__ import annotations

import json
from unittest.mock import patch

import pytest

from kream_crawler import cli


class _FakeResponse:
    def __init__(self, html: str, status_code: int = 200):
        self.text = html
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"status {self.status_code}")


def _boots_fixture_html() -> str:
    """간단한 boots 상품 HTML — Mercurial Superfly FG."""
    return (
        '<html><head>'
        '<meta property="og:title" content="Nike Mercurial Superfly 9 Elite FG Black"/>'
        '<meta property="og:image" content="https://example/boots.jpg"/>'
        '<meta name="keywords" content="DJ4977-001,나이키 머큐리얼 슈퍼플라이 9 엘리트 FG 블랙,Nike Mercurial Superfly 9 Elite FG Black"/>'
        '<script type="application/ld+json">'
        '{"@type":"Product","name":"Nike Mercurial Superfly 9 Elite FG Black","sku":"DJ4977-001"}'
        '</script>'
        '</head><body></body></html>'
    )


def _uniform_fixture_html() -> str:
    """간단한 uniform 상품 HTML — Manchester United Home 24/25."""
    return (
        '<html><head>'
        '<meta property="og:title" content="Adidas Manchester United Home 24/25 Jersey"/>'
        '<meta property="og:image" content="https://example/jersey.jpg"/>'
        '<meta name="keywords" content="MUFC-2425-HOME,아디다스 맨체스터 유나이티드 24/25 홈 저지,Adidas Manchester United Home 24/25 Jersey"/>'
        '<script type="application/ld+json">'
        '{"@type":"Product","name":"Adidas Manchester United Home 24/25 Jersey","sku":"MUFC-2425-HOME"}'
        '</script>'
        '</head><body></body></html>'
    )


class TestArgparseChoices:
    def test_category_required(self, tmp_path):
        with pytest.raises(SystemExit):
            cli.main(["--output", str(tmp_path / "out.json")])

    def test_category_invalid_rejected(self, tmp_path):
        with pytest.raises(SystemExit):
            cli.main(
                [
                    "--category", "shoes",
                    "--output", str(tmp_path / "out.json"),
                ]
            )

    def test_category_uniform_now_accepted(self, tmp_path):
        """ADR-017 §D2: --category uniform 활성화 — argparse 단계에서 거부되지 않음."""
        with patch.object(cli, "_run_uniform", return_value=0) as mock_run:
            rc = cli.main(
                [
                    "--category", "uniform",
                    "--output", str(tmp_path / "out.json"),
                ]
            )
        assert rc == 0
        mock_run.assert_called_once()


class TestRunBootsPipeline:
    def test_boots_pipeline_with_mocked_client(self, tmp_path, mocker):
        """축구화 파이프라인 — discover + fetch + normalize + export 흐름 통합."""
        output = tmp_path / "boots.json"

        # discover_boots_product_urls 가 1개 URL 반환
        mocker.patch.object(
            cli, "discover_boots_product_urls",
            return_value=["https://kream.co.kr/products/100"],
        )
        # KreamClient.get 이 fixture HTML 반환 (rate limit sleep 우회)
        mocker.patch.object(
            cli, "KreamClient",
            return_value=mocker.Mock(get=lambda url: _FakeResponse(_boots_fixture_html())),
        )

        rc = cli.main(
            [
                "--category", "boots",
                "--limit", "1",
                "--output", str(output),
            ]
        )

        assert rc == 0
        assert output.exists()
        payload = json.loads(output.read_text(encoding="utf-8"))
        # exporter 가 dict 또는 list 로 export — stats 와 items 분리
        assert "items" in payload
        assert "stats" in payload
        assert payload["stats"]["category"] == "BOOTS"
        assert payload["stats"]["silo_matched"] == 1
        assert payload["stats"]["korean_full_name_present"] == 1
        assert payload["items"][0]["bootsSpec"]["siloName"] == "Mercurial Superfly"
        assert payload["items"][0]["fullNameKo"].startswith("나이키")


class TestRunUniformPipeline:
    def test_uniform_pipeline_activates(self, tmp_path, mocker):
        """ADR-017: --category uniform 의 NotImplementedError 해제 검증."""
        output = tmp_path / "uniform.json"

        mocker.patch.object(
            cli, "discover_uniform_product_urls",
            return_value=["https://kream.co.kr/products/200"],
        )
        mocker.patch.object(
            cli, "KreamClient",
            return_value=mocker.Mock(get=lambda url: _FakeResponse(_uniform_fixture_html())),
        )

        rc = cli.main(
            [
                "--category", "uniform",
                "--limit", "1",
                "--output", str(output),
            ]
        )

        assert rc == 0
        payload = json.loads(output.read_text(encoding="utf-8"))
        assert payload["stats"]["category"] == "UNIFORM"
        assert payload["stats"]["club_matched"] == 1
        assert payload["stats"]["season_extracted"] == 1
        assert payload["stats"]["kit_type_inferred"] == 1
        spec = payload["items"][0]["uniformSpec"]
        assert spec["clubName"] == "Manchester United"
        assert spec["clubNameKo"] == "맨체스터 유나이티드"
        assert spec["season"] == "24/25"
        assert spec["league"] == "EPL"
        assert spec["kitType"] == "HOME"


class TestCrawlerBlockedExitCode:
    def test_blocked_returns_exit_code_2(self, tmp_path, mocker):
        """차단 시 exit code 2 반환 (운영자 검수 신호)."""
        from kream_crawler.http_client import CrawlerBlockedError

        mocker.patch.object(
            cli, "_run_boots",
            side_effect=CrawlerBlockedError("403"),
        )

        rc = cli.main(
            [
                "--category", "boots",
                "--limit", "1",
                "--output", str(tmp_path / "out.json"),
            ]
        )

        assert rc == 2

    def test_blocked_inside_pipeline_loop_returns_exit_code_2(self, tmp_path, mocker):
        """ADR-017 / code-reviewer M5: _run_pipeline 의 fetch 루프에서 차단 시
        광범위 except 가 삼키지 않고 main 으로 전파 → exit code 2.

        과거 PR #74 에서 같은 패턴 (광범위 except Exception 이 CrawlerBlockedError 삼킴) 회귀 차단.
        """
        from kream_crawler.http_client import CrawlerBlockedError

        mocker.patch.object(
            cli, "discover_boots_product_urls",
            return_value=[
                "https://kream.co.kr/products/100",
                "https://kream.co.kr/products/200",
            ],
        )
        # 첫 fetch 에서 CrawlerBlockedError 발생 → 즉시 중단 (loop 가 두 번째 URL 시도하면 안 됨)
        mocker.patch.object(
            cli, "KreamClient",
            return_value=mocker.Mock(
                get=mocker.Mock(side_effect=CrawlerBlockedError("403 forbidden")),
            ),
        )

        rc = cli.main(
            [
                "--category", "boots",
                "--limit", "2",
                "--output", str(tmp_path / "blocked.json"),
            ]
        )

        assert rc == 2

    def test_forbidden_path_inside_pipeline_returns_exit_code_2(self, tmp_path, mocker):
        """ForbiddenPathError 도 정책 위반 — 광범위 except 에 삼켜지면 안 됨."""
        from kream_crawler.http_client import ForbiddenPathError

        mocker.patch.object(
            cli, "discover_boots_product_urls",
            return_value=["https://kream.co.kr/products/100"],
        )
        mocker.patch.object(
            cli, "KreamClient",
            return_value=mocker.Mock(
                get=mocker.Mock(
                    side_effect=ForbiddenPathError("/my/* 차단 경로"),
                ),
            ),
        )

        # ForbiddenPathError 는 ValueError 자식이지만 main 의 except CrawlerBlockedError 에는
        # 잡히지 않으므로 ValueError 로 propagate. 운영 시 fail-fast 가 의도.
        try:
            rc = cli.main(
                [
                    "--category", "boots",
                    "--limit", "1",
                    "--output", str(tmp_path / "forbidden.json"),
                ]
            )
        except ForbiddenPathError:
            # 정책 위반이 즉시 전파됨 — 광범위 except 에 삼켜지지 않음 (회귀 차단점).
            return
        # 만약 rc 가 반환됐다면 광범위 except 에 삼켜진 것 — 회귀.
        pytest.fail(f"ForbiddenPathError 가 전파되지 않고 rc={rc} 로 종료됨 — 광범위 except 회귀")
