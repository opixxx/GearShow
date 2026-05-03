"""상품 URL 발견 단위 테스트 — search endpoint 기반."""

from __future__ import annotations

from urllib.parse import quote

import requests_mock

from kream_crawler.http_client import KreamClient
from kream_crawler.sitemap import (
    KREAM_BASE,
    discover_boots_product_urls_via_search,
    discover_uniform_product_urls,
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


# ===== ADR-017 — discover_uniform_product_urls 회귀 보호 =====


def test_discover_uniform_default_keyword_quotes_space():
    """ADR-017 §D2: default keyword '축구 유니폼' 의 공백이 quote 인코딩된다."""
    client = KreamClient()
    encoded = quote("축구 유니폼", safe="")
    expected_url = f"{KREAM_BASE}/search?keyword={encoded}&tab=products"
    with requests_mock.Mocker() as m:
        m.get(expected_url, text='<a href="/products/777">jersey</a>')
        urls = discover_uniform_product_urls(client, limit=10)

    assert urls == [f"{KREAM_BASE}/products/777"]


def test_discover_uniform_respects_limit():
    """uniform 흐름도 limit 동작 보장 — 100건 후보 중 limit=3 만 반환."""
    html = "".join(f'<a href="/products/{i}">x</a>' for i in range(500, 600))
    client = KreamClient()
    encoded = quote("축구 유니폼", safe="")
    with requests_mock.Mocker() as m:
        m.get(
            f"{KREAM_BASE}/search?keyword={encoded}&tab=products",
            text=html,
        )
        urls = discover_uniform_product_urls(client, limit=3)

    assert len(urls) == 3
    assert urls[0] == f"{KREAM_BASE}/products/500"


def test_discover_uniform_custom_keyword_overrides_default():
    """keyword 인자로 default ('축구 유니폼') 을 override 가능."""
    client = KreamClient()
    encoded = quote("저지", safe="")
    with requests_mock.Mocker() as m:
        m.get(
            f"{KREAM_BASE}/search?keyword={encoded}&tab=products",
            text='<a href="/products/888">x</a>',
        )
        urls = discover_uniform_product_urls(client, limit=1, keyword="저지")

    assert urls == [f"{KREAM_BASE}/products/888"]
