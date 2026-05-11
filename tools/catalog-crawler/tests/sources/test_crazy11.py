"""crazy11 출처 단위 테스트 (ADR-023).

EUC-KR 인코딩, JSON-LD parse, robots.txt 차단 xcode 거부, fullNameEn None 정책 등.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from catalog_crawler.sources import Category, ForbiddenPathError, SourceClient
from catalog_crawler.sources.crazy11 import (
    Crazy11Client,
    _CATEGORY_XCODES,
    _DISALLOWED_XCODES,
    _extract_branduid,
)


FIXTURE_PATH = (
    Path(__file__).parent.parent / "fixtures" / "sample_crazy11_product.html"
)


class _FakeResponse:
    """requests.Response 흉내 — text 디코딩 결과만 제공."""

    def __init__(self, content: bytes, encoding: str = "euc-kr", status_code: int = 200):
        self.content = content
        self.encoding = encoding
        self.status_code = status_code

    @property
    def text(self) -> str:
        return self.content.decode(self.encoding, errors="replace")


class TestCrazy11ClientInterface:
    def test_implements_source_client_abc(self):
        client = Crazy11Client(rate_limit_per_sec=10.0)
        assert isinstance(client, SourceClient)
        assert client.name == "crazy11"
        assert client.base_url.startswith("http://www.crazy11.co.kr")


class TestCategoryMapping:
    def test_boots_xcode_includes_main_categories(self):
        boots = _CATEGORY_XCODES[Category.BOOTS]
        assert "001" in boots   # 축구화
        assert "002" in boots   # 풋살화
        assert "257" in boots   # 축구화 (메인)
        assert len(boots) >= 3

    def test_uniform_xcode_mapped(self):
        assert "175" in _CATEGORY_XCODES[Category.UNIFORM]   # 단체/유니폼

    def test_disallowed_xcodes_not_in_any_category(self):
        """robots.txt 가 차단한 xcode 가 어느 카테고리 매핑에도 포함되지 않아야 함."""
        all_mapped = set()
        for xcodes in _CATEGORY_XCODES.values():
            all_mapped.update(xcodes)
        for disallowed in _DISALLOWED_XCODES:
            assert disallowed not in all_mapped, f"차단 xcode 가 매핑됨: {disallowed}"


class TestRobotsTxtGuard:
    def test_blocked_xcode_raises_forbidden_path_error(self):
        client = Crazy11Client(rate_limit_per_sec=10.0)
        url = "http://www.crazy11.co.kr/shop/shopbrand.html?xcode=259&type=Y"
        with pytest.raises(ForbiddenPathError, match="xcode=259"):
            client._guard_disallowed_xcode(url)

    def test_allowed_xcode_passes_guard(self):
        client = Crazy11Client(rate_limit_per_sec=10.0)
        url = "http://www.crazy11.co.kr/shop/shopbrand.html?xcode=257&type=Y"
        # 예외 없음 = OK
        client._guard_disallowed_xcode(url)


class TestParseProduct:
    """JSON-LD Product schema 추출 검증 — 실제 사이트 fixture HTML 사용."""

    def setup_method(self):
        self.client = Crazy11Client(rate_limit_per_sec=10.0)
        self.response = _FakeResponse(FIXTURE_PATH.read_bytes())

    def test_extracts_korean_name_from_json_ld(self):
        """JSON-LD Product.name 이 한글 풀네임 — 가장 권위 있는 source."""
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["name_ko"] is not None
        assert "스타킹" in raw["name_ko"] or "UT704" in raw["name_ko"]

    def test_extracts_style_code_from_sku(self):
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["style_code"] is not None
        assert raw["style_code"].isdigit() or "-" in raw["style_code"]

    def test_extracts_image_url_from_jsonld(self):
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["image_url"] is not None
        assert raw["image_url"].startswith("http")

    def test_returns_none_english_name_adr_023_d4(self):
        """ADR-023 §D4: crazy11 의 영문명 단일 source 부재 → name_en=None."""
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        assert raw["name_en"] is None

    def test_returns_none_brand_jsonld_unreliable(self):
        """JSON-LD 의 brand.name 이 사이트의 할인율 등 잘못된 값으로 채워짐 → None."""
        raw = self.client.parse_product(self.response, "http://www.crazy11.co.kr/x")
        # 정상 brand 이름 ('Nike', 'Adidas' 등) 이 아니거나 None
        assert raw["brand"] is None

    def test_preserves_source_url(self):
        url = "http://www.crazy11.co.kr/shop/shopdetail.html?branduid=773971"
        raw = self.client.parse_product(self.response, url)
        assert raw["source_url"] == url


class TestExtractBranduid:
    def test_extracts_branduid_from_query(self):
        url = "http://www.crazy11.co.kr/shop/shopdetail.html?branduid=773971&xcode=127"
        assert _extract_branduid(url) == "773971"

    def test_returns_none_when_branduid_absent(self):
        url = "http://www.crazy11.co.kr/shop/shopbrand.html?xcode=257"
        assert _extract_branduid(url) is None


class TestEncodingHandling:
    """EUC-KR 인코딩 round-trip 검증 (ADR-023 §D5)."""

    def test_fixture_is_euc_kr_encoded(self):
        """fixture 자체가 EUC-KR 인지 — UTF-8 decode 시 깨짐 확인."""
        raw_bytes = FIXTURE_PATH.read_bytes()
        # EUC-KR 디코딩은 정상
        euc_kr_text = raw_bytes.decode("euc-kr", errors="strict")
        assert "축구화" in euc_kr_text or "크레이지11" in euc_kr_text

    def test_parse_handles_euc_kr_response_text(self):
        """response.text 가 이미 EUC-KR 로 decode 된 str — 한글 정상 추출."""
        client = Crazy11Client(rate_limit_per_sec=10.0)
        response = _FakeResponse(FIXTURE_PATH.read_bytes())
        raw = client.parse_product(response, "http://www.crazy11.co.kr/x")
        # 한글 풀네임이 정상 decode 됨
        assert raw["name_ko"] is not None
        # ASCII 만 있으면 디코딩 깨진 신호
        assert not raw["name_ko"].isascii()
