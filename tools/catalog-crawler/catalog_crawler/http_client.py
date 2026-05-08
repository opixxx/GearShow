"""HTTP 클라이언트 — Rate limit + 명시적 User-Agent + 금지 경로 가드."""

from __future__ import annotations

import logging
import time
from urllib.parse import urlparse

import requests

LOGGER = logging.getLogger(__name__)

DEFAULT_USER_AGENT = "GearShow-Catalog-Bot/1.0 (+contact: opix0306@naver.com)"
"""Kream 운영자가 차단/문의가 필요할 때 식별 가능하도록 명시적 UA 사용."""

# robots.txt 가 명시적으로 차단한 두 경로. client 단에서 거부하여 사고 방지.
DISALLOWED_PATH_PREFIXES = ("/my", "/history")


class ForbiddenPathError(ValueError):
    """robots.txt 가 차단한 경로에 접근 시도."""


class CrawlerBlockedError(RuntimeError):
    """403/429 등 차단 응답 — 즉시 중단."""


class KreamClient:
    """Kream HTTP 호출 client. 1 req/sec rate limit + UA + 금지 경로 가드."""

    def __init__(
        self,
        rate_limit_per_sec: float = 1.0,
        user_agent: str = DEFAULT_USER_AGENT,
        timeout: float = 10.0,
    ) -> None:
        self._min_interval = 1.0 / rate_limit_per_sec
        self._last_request_at: float = 0.0
        self._session = requests.Session()
        self._session.headers.update({"User-Agent": user_agent})
        self._timeout = timeout

    def get(self, url: str) -> requests.Response:
        self._guard_disallowed_path(url)
        self._wait_for_rate_limit()
        LOGGER.debug("GET %s", url)
        response = self._session.get(url, timeout=self._timeout)
        self._guard_blocked_status(response)
        return response

    def _guard_disallowed_path(self, url: str) -> None:
        path = urlparse(url).path or "/"
        for prefix in DISALLOWED_PATH_PREFIXES:
            if path.startswith(prefix):
                raise ForbiddenPathError(
                    f"robots.txt 가 차단한 경로 접근 시도: {path}"
                )

    def _wait_for_rate_limit(self) -> None:
        elapsed = time.monotonic() - self._last_request_at
        if elapsed < self._min_interval:
            time.sleep(self._min_interval - elapsed)
        self._last_request_at = time.monotonic()

    def _guard_blocked_status(self, response: requests.Response) -> None:
        if response.status_code in (403, 429):
            raise CrawlerBlockedError(
                f"차단 응답 (status={response.status_code}). 운영자 확인 필요. "
                f"url={response.url}"
            )
