"""HTTP 클라이언트 — ADR-023 의 다중 출처 정책에 따라 ``sources/kream.py`` 로 이동.

기존 import path (``from catalog_crawler.http_client import KreamClient, ...``) 호환을 위해
shim 으로 re-export. 본 모듈에 추가 로직 작성 금지 — 사이트별 client 는 ``sources/<site>.py`` 에 추가.
"""

from catalog_crawler.sources.kream import (
    DEFAULT_USER_AGENT,
    DISALLOWED_PATH_PREFIXES,
    KreamClient,
)
from catalog_crawler.sources import CrawlerBlockedError, ForbiddenPathError

__all__ = [
    "DEFAULT_USER_AGENT",
    "DISALLOWED_PATH_PREFIXES",
    "KreamClient",
    "CrawlerBlockedError",
    "ForbiddenPathError",
]
