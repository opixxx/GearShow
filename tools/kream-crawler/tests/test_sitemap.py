"""상품 URL 발견 단위 테스트 — search endpoint 기반."""

from __future__ import annotations

import requests_mock

from kream_crawler.http_client import KreamClient
from kream_crawler.sitemap import (
    KREAM_BASE,
    discover_boots_product_urls_via_search,
)


def test_discover_via_search_extracts_unique_product_ids():
    html = """
    <html><body>
      <a href="/products/100">A</a>
      <a href="/products/200">B</a>
      <a href="/products/100">A duplicate</a>
      <a href="/products/300">C</a>
    </body></html>
    """
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get(
            f"{KREAM_BASE}/search?keyword=%EC%B6%95%EA%B5%AC%ED%99%94&tab=products",
            text=html,
        )
        urls = discover_boots_product_urls_via_search(client, limit=10)

    assert urls == [
        f"{KREAM_BASE}/products/100",
        f"{KREAM_BASE}/products/200",
        f"{KREAM_BASE}/products/300",
    ]


def test_discover_via_search_respects_limit():
    html = "".join(f'<a href="/products/{i}">x</a>' for i in range(100, 200))
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get(
            f"{KREAM_BASE}/search?keyword=%EC%B6%95%EA%B5%AC%ED%99%94&tab=products",
            text=html,
        )
        urls = discover_boots_product_urls_via_search(client, limit=5)

    assert len(urls) == 5
    assert urls[0] == f"{KREAM_BASE}/products/100"


def test_discover_via_search_custom_keyword():
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get(
            f"{KREAM_BASE}/search?keyword=%EC%9C%A0%EB%8B%88%ED%8F%BC&tab=products",
            text='<a href="/products/999">x</a>',
        )
        urls = discover_boots_product_urls_via_search(client, limit=1, keyword="유니폼")

    assert urls == [f"{KREAM_BASE}/products/999"]
