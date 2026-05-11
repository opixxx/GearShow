"""상품 URL 발견 — 검색 endpoint 우선, sitemap fallback.

Kream sitemap 은 카테고리 정보 없이 모든 상품을 ID 오름차순으로 나열하므로
축구화 도달 효율이 매우 낮다. 따라서 1차로 검색 페이지(`/search?keyword=축구화&tab=products`)
의 SSR HTML 에서 `/products/{id}` 를 직접 추출한다 (단일 요청 ~3.5MB).
"""

from __future__ import annotations

import logging
import re
from urllib.parse import quote

# 외부 사이트 XML 파싱은 defusedxml 사용 — XXE/billion laughs 차단 (PR-B 보강).
# stdlib xml.etree 는 entity expansion 에 취약하다고 알려져 있어 외부 입력 신뢰 영역엔 부적합.
from defusedxml import ElementTree as ET

from catalog_crawler.http_client import KreamClient

LOGGER = logging.getLogger(__name__)

KREAM_BASE = "https://kream.co.kr"
SITEMAP_URL = f"{KREAM_BASE}/sitemap.xml"
SITEMAP_NS = "{http://www.sitemaps.org/schemas/sitemap/0.9}"

PRODUCT_ID_PATTERN = re.compile(r"/products/(\d+)")


def fetch_sitemap_index(client: KreamClient, sitemap_url: str = SITEMAP_URL) -> list[str]:
    """최상위 sitemap.xml 또는 sitemapindex.xml 에서 하위 sitemap URL 들을 추출한다.

    sitemapindex 형식: <sitemap><loc>...</loc></sitemap>
    urlset 형식 (단일 sitemap): <url><loc>...</loc></url>
    """
    response = client.get(sitemap_url)
    response.raise_for_status()
    # forbid_dtd=True: DTD declaration 자체 금지 (PR-B test-writer M1).
    # defusedxml default 는 forbid_dtd=False 라 DTD 안의 external SYSTEM 참조를
    # 차단하지 못한다. 정상 Kream sitemap 에는 DOCTYPE 없으므로 안전하게 강제.
    root = ET.fromstring(response.content, forbid_dtd=True)

    if root.tag.endswith("sitemapindex"):
        return [
            loc.text.strip()
            for loc in root.findall(f"{SITEMAP_NS}sitemap/{SITEMAP_NS}loc")
            if loc.text
        ]
    if root.tag.endswith("urlset"):
        return [sitemap_url]
    LOGGER.warning("알 수 없는 sitemap 루트 태그: %s", root.tag)
    return []


def fetch_product_urls(
    client: KreamClient,
    sitemap_url: str,
    keyword: str | None = None,
) -> list[str]:
    """하위 sitemap (urlset) 에서 상품 URL 리스트를 추출한다.

    keyword 가 주어지면 URL 에 keyword 가 포함된 것만 (예: "products" 또는 카테고리 슬러그) 필터링.
    """
    response = client.get(sitemap_url)
    response.raise_for_status()
    root = ET.fromstring(response.content, forbid_dtd=True)

    urls: list[str] = []
    for loc in root.findall(f"{SITEMAP_NS}url/{SITEMAP_NS}loc"):
        if not loc.text:
            continue
        url = loc.text.strip()
        if keyword and keyword not in url:
            continue
        urls.append(url)
    return urls


def discover_boots_product_urls_via_search(
    client: KreamClient,
    limit: int,
    keyword: str = "축구화",
) -> list[str]:
    """검색 페이지 SSR HTML 에서 ``/products/{id}`` 를 추출한다 — sources/kream.py 의 shim.

    ADR-023 §D8 의 backward-compat 정책: sitemap.py 의 기존 함수는 sources/kream.py 의
    구현 (페이지네이션 포함) 으로 위임. 본 PR (kream-search-pagination) 의 페이지네이션
    효과를 기존 import 경로에도 적용.
    """
    from catalog_crawler.sources.kream import _discover_via_search
    return _discover_via_search(client, limit=limit, keyword=keyword)


def discover_boots_product_urls(
    client: KreamClient, limit: int = 50
) -> list[str]:
    """축구화 상품 URL 발견. 검색 endpoint 우선 사용."""
    return discover_boots_product_urls_via_search(client, limit=limit)


def discover_uniform_product_urls(
    client: KreamClient, limit: int = 50, keyword: str = "축구 유니폼"
) -> list[str]:
    """유니폼 상품 URL 발견 (ADR-017). 검색 keyword 는 '축구 유니폼' / '저지' 등.

    축구화 흐름과 동일한 검색 SSR HTML 파싱 — keyword 만 교체.
    """
    return discover_boots_product_urls_via_search(client, limit=limit, keyword=keyword)
