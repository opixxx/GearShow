"""상품 URL 발견 단위 테스트 — search endpoint 기반."""

from __future__ import annotations

from urllib.parse import quote

import pytest
import requests_mock

from kream_crawler.http_client import KreamClient
from kream_crawler.sitemap import (
    KREAM_BASE,
    discover_boots_product_urls_via_search,
    discover_uniform_product_urls,
    fetch_sitemap_index,
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


# ===== PR-B: defusedxml XXE 보호 회귀 =====


# PR-B (code-reviewer Major #2 + test-writer M1): defusedxml 이 외부 XML attack vector 를
# 차단한다. stdlib xml.etree 는 모든 vector 에 취약 — 4종 vector parametrize 로 회귀 보호.
_XXE_VECTORS = [
    pytest.param(
        '<?xml version="1.0"?>\n'
        '<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>\n'
        '<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        '  <sitemap><loc>&xxe;</loc></sitemap>\n'
        '</sitemapindex>',
        id="external_entity",
    ),
    pytest.param(
        # billion laughs — 재귀 entity expansion (DoS)
        '<?xml version="1.0"?>\n'
        '<!DOCTYPE foo [\n'
        '  <!ENTITY a "lol">\n'
        '  <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">\n'
        '  <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">\n'
        ']>\n'
        '<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        '  <sitemap><loc>&c;</loc></sitemap>\n'
        '</sitemapindex>',
        id="billion_laughs",
    ),
    pytest.param(
        # external DTD 참조 (네트워크 호출 유도)
        '<?xml version="1.0"?>\n'
        '<!DOCTYPE foo SYSTEM "http://attacker.example/evil.dtd">\n'
        '<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        '  <sitemap><loc>http://example/</loc></sitemap>\n'
        '</sitemapindex>',
        id="external_dtd",
    ),
    pytest.param(
        # parameter entity — DTD 안에서 entity 정의 자체에 외부 참조
        '<?xml version="1.0"?>\n'
        '<!DOCTYPE foo [\n'
        '  <!ENTITY % pe SYSTEM "file:///etc/passwd">\n'
        '  %pe;\n'
        ']>\n'
        '<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        '</sitemapindex>',
        id="parameter_entity",
    ),
]


@pytest.mark.parametrize("payload", _XXE_VECTORS)
def test_fetch_sitemap_index_rejects_xxe_attack_vectors(payload):
    """defusedxml 이 4종 XML attack vector 를 모두 차단한다.

    각 vector 가 raise 하는 exception 의 정확한 클래스명은 defusedxml 버전 의존 —
    module 또는 클래스 이름으로 검증.
    """
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get(f"{KREAM_BASE}/sitemap.xml", text=payload)
        with pytest.raises(Exception) as exc_info:
            fetch_sitemap_index(client)

        exc_module = type(exc_info.value).__module__
        exc_name = type(exc_info.value).__name__
        assert "defusedxml" in exc_module or "Forbidden" in exc_name, (
            f"defusedxml 차단 실패 — exc={exc_module}.{exc_name}"
        )
