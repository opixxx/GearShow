"""CLI 통합 테스트 — --mirror-images 옵션 동작 검증.

ADR-021 §D1: --mirror-images on 으로 export 된 JSON 안에 외부 도메인 흔적 0건.
"""

from __future__ import annotations

import json

import boto3
import pytest
from moto import mock_aws

from catalog_crawler import cli


@pytest.fixture
def isolated_aws_env(monkeypatch):
    """moto 와 함께 사용할 AWS credential 환경변수 격리."""
    monkeypatch.setenv("AWS_ACCESS_KEY_ID", "testing")
    monkeypatch.setenv("AWS_SECRET_ACCESS_KEY", "testing")
    monkeypatch.setenv("AWS_SECURITY_TOKEN", "testing")
    monkeypatch.setenv("AWS_SESSION_TOKEN", "testing")
    monkeypatch.setenv("AWS_DEFAULT_REGION", "ap-northeast-2")


def _fake_item(image_url: str) -> dict:
    """boots CatalogItem 형태의 fake item."""
    return {
        "category": "BOOTS",
        "modelCode": "TEST-001",
        "officialImageUrl": image_url,
        "fullNameKo": "테스트",
        "fullNameEn": "Test",
        "brand": "Test",
        "bootsSpec": {
            "siloName": None, "siloNameKo": None,
            "studType": None, "releaseYear": None,
            "surfaceType": None, "extraSpecJson": None,
        },
        "uniformSpec": None,
    }


def _stub_pipeline(monkeypatch, image_url: str, *, response_body: bytes = b"\x89PNG"):
    """discover/parse/normalize/KreamClient 를 모두 mock 으로 교체."""

    monkeypatch.setattr(
        cli, "discover_boots_product_urls",
        lambda client, limit: ["https://kream.co.kr/products/1"],
    )
    monkeypatch.setattr(
        cli, "parse_product_html",
        lambda html, source_url=None: {
            "name": "Test", "name_ko": "테스트", "name_en": "Test",
            "style_code": "TEST-001", "brand": "Test",
            "image_url": image_url, "source_url": source_url,
        },
    )
    monkeypatch.setattr(
        cli, "to_boots_item",
        lambda raw, silos: _fake_item(image_url),
    )

    class _FakeResp:
        def __init__(self):
            self.status_code = 200
            self.text = "<html></html>"
            self.content = response_body
            self.headers = {"Content-Type": "image/png"}

    class _FakeClient:
        def __init__(self, *_, **__):
            pass

        def get(self, url):
            return _FakeResp()

    monkeypatch.setattr(cli, "KreamClient", _FakeClient)


def _create_bucket(bucket: str, region: str = "ap-northeast-2") -> None:
    boto3.client("s3", region_name=region).create_bucket(
        Bucket=bucket,
        CreateBucketConfiguration={"LocationConstraint": region},
    )


def test_mirror_images_off_keeps_external_urls(monkeypatch, tmp_path):
    """--mirror-images 없이 실행 → 기존 동작 (외부 CDN URL 그대로) 보존 — 회귀 가드."""
    output_path = tmp_path / "boots.json"
    fake_url = "https://kream-phinf.pstatic.net/path/a.png"
    _stub_pipeline(monkeypatch, fake_url)

    rc = cli.main([
        "--category", "boots",
        "--limit", "1",
        "--output", str(output_path),
        "--rate-limit", "0",
    ])
    assert rc == 0

    data = json.loads(output_path.read_text(encoding="utf-8"))
    assert data["items"][0]["officialImageUrl"] == fake_url


@mock_aws
def test_mirror_images_replaces_external_urls(monkeypatch, tmp_path, isolated_aws_env):
    """--mirror-images on → export JSON 안에 외부 도메인 0건 + 자체 URL 로 교체."""
    bucket = "test-bucket"
    _create_bucket(bucket)
    monkeypatch.setenv("AWS_S3_BUCKET", bucket)

    output_path = tmp_path / "boots.json"
    fake_url = "https://kream-phinf.pstatic.net/path/a.png"
    _stub_pipeline(monkeypatch, fake_url, response_body=b"\x89PNG fake-bytes")

    rc = cli.main([
        "--category", "boots",
        "--limit", "1",
        "--output", str(output_path),
        "--rate-limit", "0",
        "--mirror-images",
        "--s3-bucket", bucket,
    ])
    assert rc == 0

    raw = output_path.read_text(encoding="utf-8")
    # 핵심 검증: 외부 도메인 흔적 0
    assert "kream-phinf" not in raw
    assert "pstatic" not in raw

    data = json.loads(raw)
    new_url = data["items"][0]["officialImageUrl"]
    assert new_url.startswith(f"https://{bucket}.s3.ap-northeast-2.amazonaws.com/")
    assert new_url.endswith("/catalog-images/boots/TEST-001.png")


def test_mirror_images_missing_bucket_returns_2(monkeypatch, tmp_path):
    """--mirror-images on + bucket 없음 → exit code 2 (fail fast)."""
    monkeypatch.delenv("AWS_S3_BUCKET", raising=False)
    rc = cli.main([
        "--category", "boots",
        "--limit", "1",
        "--output", str(tmp_path / "x.json"),
        "--mirror-images",
    ])
    assert rc == 2
