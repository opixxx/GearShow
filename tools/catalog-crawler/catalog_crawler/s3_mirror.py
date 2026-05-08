"""S3 미러링 — 외부 CDN 이미지를 GearShow 자체 S3 로 업로드.

ADR-021: 운영 catalog 의 official_image_url 이 외부 도메인을 갖지 않도록
crawler export 단계에서 다운로드 → 자체 S3 업로드 → URL 교체한다.

호출자가 rate-limited http_get callable 을 주입한다 — 외부 사이트 fetch 시
``KreamClient`` 의 1 req/sec 가드를 우회하지 않게 함 (ADR-021 §D1).

AWS credential 은 boto3 default chain 으로 자동 resolve (~/.aws/credentials,
AWS_PROFILE, AWS_ACCESS_KEY_ID 등). 코드 하드코딩 금지 (ADR-021 §D6).
"""

from __future__ import annotations

import logging
from urllib.parse import urlparse

import boto3
from botocore.exceptions import ClientError

LOGGER = logging.getLogger(__name__)

# path suffix 추론 시 인식할 이미지 확장자.
# `.jpeg` 는 일관성을 위해 `.jpg` 로 정규화 (S3 key 멱등성 확보).
_KNOWN_IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".webp", ".gif")


def build_s3_key(category: str, model_code: str, src_url: str) -> str:
    """S3 객체 key 생성 — ``<category-lower>/<modelCode><ext>`` 형식.

    확장자: src URL path 의 suffix 우선 (.png/.jpg/.webp 등). 추론 실패 시 ``.bin`` fallback.
    ``.jpeg`` 는 ``.jpg`` 로 정규화 — 동일 콘텐츠가 다른 key 로 분기되는 멱등성 함정 차단 (ADR-021 §D2).
    같은 (category, modelCode) 두 번 호출 시 같은 key — 멱등성 보장.

    :raises ValueError: model_code 가 None/빈 문자열, 또는 src_url 이 유효한 HTTP(S) URL 이 아님 (scheme + host 검증).
    """
    if not model_code:
        raise ValueError("model_code 가 비어있어 S3 key 를 생성할 수 없습니다")
    if not category:
        raise ValueError("category 가 비어있어 S3 key 를 생성할 수 없습니다")

    parsed = urlparse(src_url) if src_url else None
    # scheme + netloc 양쪽 검증 — `https:///path` 같은 빈 host URL 차단 (code-reviewer Minor #1).
    if not parsed or parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ValueError(f"src_url 이 유효한 HTTP(S) URL 이 아닙니다: {src_url!r}")

    path = parsed.path.lower()
    ext = ".bin"
    for candidate in _KNOWN_IMAGE_EXTS:
        if path.endswith(candidate):
            # `.jpeg` → `.jpg` 정규화 — 동일 콘텐츠가 다른 key 로 분기되는 것 차단
            ext = ".jpg" if candidate == ".jpeg" else candidate
            break

    return f"{category.lower()}/{model_code}{ext}"


class S3Mirror:
    """외부 CDN 이미지를 자체 S3 로 미러링하는 클라이언트.

    AWS credential 은 표준 위치 (~/.aws/credentials, AWS_PROFILE,
    AWS_ACCESS_KEY_ID/SECRET) 에서 자동 resolve. 코드 하드코딩 금지 (ADR-021 §D6).
    """

    def __init__(
        self,
        bucket: str,
        prefix: str = "catalog-images",
        region: str = "ap-northeast-2",
    ):
        if not bucket:
            raise ValueError("bucket 이름은 필수입니다")
        self._bucket = bucket
        self._prefix = prefix.rstrip("/")
        self._region = region
        # boto3 default credential chain 사용 — 운영자 환경에서 자동 resolve.
        self._client = boto3.client("s3", region_name=region)

    def upload(self, src_url: str, key: str, *, http_get) -> str:
        """external URL 다운로드 → S3 업로드 → public URL 반환.

        :param src_url: 다운로드 source URL (예: 외부 CDN)
        :param key: :func:`build_s3_key` 결과 (prefix 제외).
        :param http_get: ``callable(url) -> response`` — :code:`KreamClient.get` 등
            rate-limited fetcher. 1 req/sec 가드 우회 차단을 위해 호출자가 주입.
            response 는 :attr:`content` (bytes) 와 :attr:`headers` 를 가진다.
        :returns: 업로드된 S3 객체의 public URL.
        :raises ClientError: S3 put_object 실패 시. 호출자가 partial dump 후 raise.
        """
        response = http_get(src_url)
        body = response.content
        content_type = response.headers.get("Content-Type", "application/octet-stream")
        full_key = f"{self._prefix}/{key}"

        LOGGER.info(
            "S3 업로드 시작: src=%s → s3://%s/%s (%d bytes, %s)",
            src_url, self._bucket, full_key, len(body), content_type,
        )

        try:
            self._client.put_object(
                Bucket=self._bucket,
                Key=full_key,
                Body=body,
                ContentType=content_type,
            )
        except ClientError as exc:
            LOGGER.error(
                "S3 업로드 실패: bucket=%s key=%s error=%s",
                self._bucket, full_key, exc,
            )
            raise

        return f"https://{self._bucket}.s3.{self._region}.amazonaws.com/{full_key}"
