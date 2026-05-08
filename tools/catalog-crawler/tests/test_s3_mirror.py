"""s3_mirror 단위 테스트 — moto 로 AWS S3 mock."""

from __future__ import annotations

import boto3
import pytest
from moto import mock_aws

from catalog_crawler.s3_mirror import S3Mirror, build_s3_key


# === build_s3_key ============================================================

def test_build_s3_key_extracts_png_extension():
    key = build_s3_key("BOOTS", "P1GA246550", "https://example.com/path/a.png")
    assert key == "boots/P1GA246550.png"


def test_build_s3_key_normalizes_jpeg_to_jpg():
    """`.jpeg` 와 `.jpg` 는 같은 콘텐츠 — S3 key 멱등성 위해 `.jpg` 로 정규화."""
    key = build_s3_key("UNIFORM", "X-100", "https://example.com/path/img.jpeg")
    assert key == "uniform/X-100.jpg"


def test_build_s3_key_falls_back_to_bin_when_extension_unknown():
    key = build_s3_key("BOOTS", "M-200", "https://example.com/no-suffix")
    assert key == "boots/M-200.bin"


def test_build_s3_key_lowercases_category():
    key = build_s3_key("Boots", "MC", "https://example.com/a.png")
    assert key == "boots/MC.png"


def test_build_s3_key_idempotent_for_same_inputs():
    """ADR-021 §D2 멱등성: 같은 (category, modelCode, src_url) → 같은 key."""
    a = build_s3_key("BOOTS", "P1GA246550", "https://example.com/a.png")
    b = build_s3_key("BOOTS", "P1GA246550", "https://example.com/a.png")
    assert a == b


def test_build_s3_key_rejects_empty_model_code():
    with pytest.raises(ValueError, match="model_code"):
        build_s3_key("BOOTS", "", "https://example.com/a.png")


def test_build_s3_key_rejects_none_model_code():
    with pytest.raises(ValueError, match="model_code"):
        build_s3_key("BOOTS", None, "https://example.com/a.png")  # type: ignore[arg-type]


def test_build_s3_key_rejects_non_http_url():
    with pytest.raises(ValueError, match="HTTP"):
        build_s3_key("BOOTS", "X", "ftp://example.com/a.png")


def test_build_s3_key_rejects_empty_host_url():
    """`https:///path` 처럼 scheme 만 있고 netloc 비어있는 URL 차단."""
    with pytest.raises(ValueError, match="HTTP"):
        build_s3_key("BOOTS", "X", "https:///path/a.png")


def test_build_s3_key_rejects_empty_category():
    with pytest.raises(ValueError, match="category"):
        build_s3_key("", "X", "https://example.com/a.png")


# === S3Mirror.upload =========================================================

class _FakeResponse:
    """KreamClient.get 의 response 형태 흉내 — .content + .headers."""

    def __init__(self, content: bytes, content_type: str = "image/png"):
        self.content = content
        self.headers = {"Content-Type": content_type}


def _create_bucket(bucket: str, region: str = "ap-northeast-2") -> None:
    boto3.client("s3", region_name=region).create_bucket(
        Bucket=bucket,
        CreateBucketConfiguration={"LocationConstraint": region},
    )


@mock_aws
def test_upload_calls_put_object_with_correct_key_and_returns_public_url():
    bucket = "test-bucket"
    _create_bucket(bucket)

    mirror = S3Mirror(bucket=bucket)
    body = b"\x89PNG fake-image-bytes"
    new_url = mirror.upload(
        "https://example.com/a.png",
        "boots/P1GA246550.png",
        http_get=lambda url: _FakeResponse(body),
    )

    expected_url = (
        f"https://{bucket}.s3.ap-northeast-2.amazonaws.com/catalog-images/boots/P1GA246550.png"
    )
    assert new_url == expected_url

    obj = boto3.client("s3", region_name="ap-northeast-2").get_object(
        Bucket=bucket,
        Key="catalog-images/boots/P1GA246550.png",
    )
    assert obj["Body"].read() == body
    assert obj["ContentType"] == "image/png"


@mock_aws
def test_upload_uses_custom_prefix():
    bucket = "test-bucket"
    _create_bucket(bucket)

    mirror = S3Mirror(bucket=bucket, prefix="custom-prefix")
    new_url = mirror.upload(
        "https://example.com/a.png",
        "boots/X.png",
        http_get=lambda u: _FakeResponse(b"x"),
    )
    assert "custom-prefix/boots/X.png" in new_url


@mock_aws
def test_upload_idempotent_second_call_overwrites_same_key():
    """ADR-021 §D2: 같은 key 두 번 업로드 → 같은 URL 반환 + 객체 overwrite."""
    bucket = "test-bucket"
    _create_bucket(bucket)

    mirror = S3Mirror(bucket=bucket)
    url1 = mirror.upload(
        "https://example.com/a.png", "boots/X.png",
        http_get=lambda u: _FakeResponse(b"first"),
    )
    url2 = mirror.upload(
        "https://example.com/a.png", "boots/X.png",
        http_get=lambda u: _FakeResponse(b"second"),
    )
    assert url1 == url2

    obj = boto3.client("s3", region_name="ap-northeast-2").get_object(
        Bucket=bucket, Key="catalog-images/boots/X.png",
    )
    assert obj["Body"].read() == b"second"


@mock_aws
def test_upload_uses_injected_http_get_callable():
    """rate-limited fetcher 주입 검증 — 별도 requests.get 으로 우회 안 함."""
    bucket = "test-bucket"
    _create_bucket(bucket)

    calls: list[str] = []

    def http_get(url: str):
        calls.append(url)
        return _FakeResponse(b"x")

    mirror = S3Mirror(bucket=bucket)
    mirror.upload("https://example.com/a.png", "boots/X.png", http_get=http_get)

    assert calls == ["https://example.com/a.png"]


def test_s3_mirror_rejects_empty_bucket():
    with pytest.raises(ValueError, match="bucket"):
        S3Mirror(bucket="")
