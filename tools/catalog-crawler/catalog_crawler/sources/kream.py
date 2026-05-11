"""Kream 출처 SourceClient (ADR-023 통합 — 기존 http_client + sitemap + product_parser 의 Kream 부분 흡수).

ADR-017 §D1: ``<meta name="keywords">`` 의 콤마 구분 3-token (modelCode / 한국어명 / 영문명) 이
한국어 풀네임의 1차 source.

ADR-021 §D7: ``KreamClient``, ``KREAM_BASE`` 같은 외부 사이트 식별자는 의도적 유지 —
어떤 사이트를 fetching 하는지 명시되어야 잘못된 사이트 fetch 차단 가능.
"""

from __future__ import annotations

import logging
import re
import time
from typing import ClassVar
from urllib.parse import quote, urlparse

import requests
from bs4 import BeautifulSoup

from catalog_crawler.sources import (
    Category,
    CrawlerBlockedError,
    ForbiddenPathError,
    RawProduct,
    SourceClient,
)

LOGGER = logging.getLogger(__name__)

KREAM_BASE = "https://kream.co.kr"

DEFAULT_USER_AGENT = "GearShow-Catalog-Bot/1.0 (+contact: opix0306@naver.com)"
"""Kream 운영자가 차단/문의가 필요할 때 식별 가능하도록 명시적 UA 사용."""

# robots.txt 가 명시적으로 차단한 두 경로. client 단에서 거부하여 사고 방지.
DISALLOWED_PATH_PREFIXES = ("/my", "/history")

PRODUCT_ID_PATTERN = re.compile(r"/products/(\d+)")


class KreamClient(SourceClient):
    """Kream HTTP 호출 client. 1 req/sec rate limit + UA + 금지 경로 가드.

    ADR-023 의 ``SourceClient`` 인터페이스 구현. 기존 시그니처 (``get(url)``) 도
    backward-compat 으로 유지 — 외부 사용처 (`sitemap.py`, `cli.py`) 점진 갱신.
    """

    name: ClassVar[str] = "kream"
    base_url: ClassVar[str] = KREAM_BASE

    def __init__(
        self,
        rate_limit_per_sec: float = 1.0,
        user_agent: str = DEFAULT_USER_AGENT,
        timeout: float = 10.0,
    ) -> None:
        self._min_interval = 1.0 / rate_limit_per_sec if rate_limit_per_sec > 0 else 0.0
        self._last_request_at: float = 0.0
        self._session = requests.Session()
        self._session.headers.update({"User-Agent": user_agent})
        self._timeout = timeout

    # === SourceClient 인터페이스 ===

    def discover_product_urls(self, category: Category, limit: int) -> list[str]:
        keyword = {
            Category.BOOTS: "축구화",
            Category.UNIFORM: "축구 유니폼",
        }[category]
        return _discover_via_search(self, limit=limit, keyword=keyword)

    def fetch_product(self, url: str) -> requests.Response:
        return self.get(url)

    def parse_product(
        self, response: requests.Response, source_url: str
    ) -> RawProduct:
        return _parse_product_html(response.text, source_url=source_url)

    # === 기존 인터페이스 (backward-compat) ===

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


# ===== Kream 검색 endpoint 기반 discover =====


def _discover_via_search(
    client: KreamClient, limit: int, keyword: str
) -> list[str]:
    """검색 페이지 SSR HTML 에서 ``/products/{id}`` 를 추출한다.

    Kream URL: ``/search?keyword=<keyword>&tab=products``. 단일 응답에 검색 결과
    상품 ID 가 포함됨. 중복 제거 + 등장 순서(검색 관련도 순) 유지. limit 도달 시 즉시 종료.
    """
    encoded = quote(keyword, safe="")
    url = f"{KREAM_BASE}/search?keyword={encoded}&tab=products"
    response = client.get(url)
    response.raise_for_status()

    seen: set[str] = set()
    product_urls: list[str] = []
    for match in PRODUCT_ID_PATTERN.finditer(response.text):
        product_id = match.group(1)
        if product_id in seen:
            continue
        seen.add(product_id)
        product_urls.append(f"{KREAM_BASE}/products/{product_id}")
        if len(product_urls) >= limit:
            break

    LOGGER.info("검색 결과 상품 URL: %d (keyword=%s)", len(product_urls), keyword)
    return product_urls


# ===== Kream 상품 상세 HTML parser =====


def _parse_product_html(html: str, source_url: str | None = None) -> RawProduct:
    """Kream 상품 상세 HTML 을 파싱하여 ``RawProduct`` 반환 (ADR-017 §D1).

    추출 우선순위:
    1. ``<meta keywords>`` (modelCode + 한국어명 + 영문명) — 가장 권위 있는 source
    2. JSON-LD ``Product.sku`` (style_code fallback)
    3. JSON-LD ``BreadcrumbList`` (brand)
    4. og:title / og:image
    5. 본문 라벨 (``<dt>스타일 코드</dt><dd>...</dd>``)
    """
    # 기존 product_parser.parse_product_html 의 로직을 재사용 — 본 PR 단계에선
    # product_parser.parse_product_html 을 직접 호출 (Step 3 가 완전 이동 시 인라인 처리).
    # backward-compat: catalog_crawler.product_parser 가 RawProduct 를 sources 에서 re-export 한 상태.
    from catalog_crawler.product_parser import parse_product_html as _parse

    return _parse(html, source_url=source_url)
