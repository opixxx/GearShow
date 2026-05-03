"""상품 URL 발견 — 검색 endpoint 우선, sitemap fallback.

Kream sitemap 은 카테고리 정보 없이 모든 상품을 ID 오름차순으로 나열하므로
축구화 도달 효율이 매우 낮다. 따라서 1차로 검색 페이지(`/search?keyword=축구화&tab=products`)
의 SSR HTML 에서 `/products/{id}` 를 직접 추출한다 (단일 요청 ~3.5MB).
"""

from __future__ import annotations

import logging
import re
from urllib.parse import quote
from xml.etree import ElementTree as ET

from kream_crawler.http_client import KreamClient

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
    root = ET.fromstring(response.content)

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
    root = ET.fromstring(response.content)

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
    """검색 페이지 SSR HTML 에서 `/products/{id}` 를 추출한다.

    Kream URL: `/search?keyword=<keyword>&tab=products`. 단일 응답에 검색 결과 상품 ID 가 포함됨.
    중복 제거 + 등장 순서(검색 관련도 순) 유지. limit 도달 시 즉시 종료.
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


def discover_boots_product_urls(
    client: KreamClient, limit: int = 50
) -> list[str]:
    """축구화 상품 URL 발견. 검색 endpoint 우선 사용."""
    return discover_boots_product_urls_via_search(client, limit=limit)
