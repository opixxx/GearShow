"""Kream 상품 상세 페이지 → raw dict 추출.

추출 실패 시 None 으로 두고 호출자에게 분류 위임 (정규화 단계에서 silo/studType 매칭으로 보강).

Kream 페이지 구조 (실측):
- og:title, og:image 메타 태그 (영문 상품명, CDN 이미지)
- JSON-LD `Product` schema 안에 sku(스타일 코드), productID 노출
- JSON-LD `BreadcrumbList` schema 의 position=2 가 브랜드명
- breadcrumb 컴포넌트는 SSR 단계에서 노출되지 않음 (SPA 후 렌더링)
"""

from __future__ import annotations

import json
import logging
import re
from typing import TypedDict

from bs4 import BeautifulSoup

LOGGER = logging.getLogger(__name__)


# RawProduct 는 ADR-023 의 다중 출처 정책에 따라 sources/__init__ 에 통합 정의됨.
# 기존 import path (`from catalog_crawler.product_parser import RawProduct`) 호환을 위해 re-export.
from catalog_crawler.sources import RawProduct  # noqa: F401


def parse_keywords(
    keywords: str | None,
) -> tuple[str | None, str | None, str | None]:
    """Kream `<meta name="keywords">` 의 콤마 구분 토큰을 (modelCode, name_ko, name_en) 으로 분리.

    실측 형식:
    "AT5889-174,나이키 프리미어 3 FG 화이트 메탈릭 골드,Nike Premier 3 FG White Metallic Gold"

    토큰 < 3 개 또는 빈 입력은 (None, None, None) — 호출자가 fallback (og:title, JSON-LD 등) 책임.
    1-token 만 있어도 modelCode 로 추정하지 않는다 (오추론보다 누락이 안전).
    """
    if not keywords:
        return None, None, None
    parts = [p.strip() for p in keywords.split(",")]
    if len(parts) >= 3:
        return parts[0] or None, parts[1] or None, parts[2] or None
    return None, None, None


def parse_product_html(html: str, source_url: str | None = None) -> RawProduct:
    """상품 상세 HTML 을 파싱하여 raw dict 를 반환.

    추출 우선순위:
    1. JSON-LD Product schema → sku, productID (가장 정확)
    2. JSON-LD BreadcrumbList → brand (position=2)
    3. og:title / og:image 메타 태그
    4. fallback: 본문 라벨 (`<dt>스타일 코드</dt><dd>...</dd>` 등)
    """
    soup = BeautifulSoup(html, "lxml")

    name = _meta(soup, "og:title")
    image_url = _meta(soup, "og:image")

    # ADR-017 §D1: keywords 메타에서 (modelCode, name_ko, name_en) 추출.
    # 사전 매칭에 한국어 풀네임이 가장 가치 있는 신호 — JSON-LD/og:title 보다 우선.
    keywords_model_code, name_ko, name_en = parse_keywords(_meta(soup, "keywords"))

    jsonld_product = _extract_jsonld_by_type(soup, "Product")
    jsonld_breadcrumb = _extract_jsonld_by_type(soup, "BreadcrumbList")

    style_code = (
        keywords_model_code
        or (jsonld_product or {}).get("sku")
        or _extract_labeled_value(soup, ("스타일 코드", "Style code", "Model"))
    )
    release_date = _extract_labeled_value(soup, ("발매일", "Release date", "출시일"))
    brand = (
        _extract_brand_from_breadcrumb(jsonld_breadcrumb)
        or _extract_labeled_value(soup, ("브랜드", "Brand"))
        or _guess_brand_from_name(name_en or name)
    )
    category_path = _extract_path_from_breadcrumb(jsonld_breadcrumb)

    return RawProduct(
        name=name,
        name_ko=name_ko,
        name_en=name_en,
        brand=brand,
        style_code=style_code,
        release_date=_normalize_date(release_date),
        image_url=image_url,
        category_path=category_path,
        source_url=source_url,
    )


def _extract_jsonld_by_type(soup: BeautifulSoup, target_type: str) -> dict | None:
    """`<script type="application/ld+json">` 들 중 `@type` 이 일치하는 첫 객체 반환."""
    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        text = script.string or script.get_text() or ""
        if not text.strip():
            continue
        try:
            data = json.loads(text)
        except json.JSONDecodeError:
            continue
        candidates = data if isinstance(data, list) else [data]
        for entry in candidates:
            if isinstance(entry, dict) and entry.get("@type") == target_type:
                return entry
    return None


def _extract_brand_from_breadcrumb(breadcrumb: dict | None) -> str | None:
    """BreadcrumbList 의 position=2 (보통 브랜드) name 추출."""
    if not breadcrumb:
        return None
    for entry in breadcrumb.get("itemListElement", []):
        if isinstance(entry, dict) and entry.get("position") == 2:
            name = entry.get("name")
            if name:
                return name.strip()
    return None


def _extract_path_from_breadcrumb(breadcrumb: dict | None) -> str | None:
    """BreadcrumbList 모든 position 의 name 을 ' > ' 로 연결."""
    if not breadcrumb:
        return None
    items = breadcrumb.get("itemListElement", [])
    names = []
    for entry in sorted(
        (e for e in items if isinstance(e, dict)),
        key=lambda e: e.get("position", 0),
    ):
        if name := entry.get("name"):
            names.append(name.strip())
    return " > ".join(names) if names else None


def is_boots_product(raw: RawProduct) -> bool:
    """축구화 카테고리 상품인지 단순 휴리스틱으로 판정.

    breadcrumb 또는 상품명에 "축구화" / 사일로 키워드 존재 여부.
    sitemap 에서 카테고리가 안 잡히는 환경에서 1차 필터.
    """
    haystack = " ".join(
        filter(None, [raw.get("category_path"), raw.get("name"), raw.get("name_ko")])
    ).lower()
    boots_keywords = ("축구화", "soccer", "football boots", "futsal")
    return any(k in haystack for k in boots_keywords)


def _meta(soup: BeautifulSoup, prop: str) -> str | None:
    tag = soup.find("meta", attrs={"property": prop}) or soup.find(
        "meta", attrs={"name": prop}
    )
    if tag and tag.get("content"):
        return tag["content"].strip()
    return None


def _extract_labeled_value(soup: BeautifulSoup, labels: tuple[str, ...]) -> str | None:
    """본문에서 라벨 옆/아래 값을 추출. `<script>` 태그 내부는 검색 대상에서 제외."""
    for label in labels:
        # 패턴 1: <dt>라벨</dt><dd>값</dd>
        dt = soup.find(
            lambda tag: tag.name == "dt" and label in tag.get_text()
        )
        if dt:
            dd = dt.find_next_sibling("dd")
            if dd and dd.get_text(strip=True):
                return dd.get_text(strip=True)
        # 패턴 2: 같은 부모 안에 라벨 + 인접 텍스트. script/style 내부는 제외.
        node = soup.find(
            string=lambda s: bool(s)
            and label in s
            and s.parent is not None
            and s.parent.name not in ("script", "style")
        )
        if node and node.parent:
            sibling = node.parent.find_next_sibling()
            if sibling and sibling.get_text(strip=True):
                value = sibling.get_text(strip=True)
                if value and value != label:
                    return value
    return None


def _guess_brand_from_name(name: str | None) -> str | None:
    """상품명 prefix 로 브랜드 추측 (라벨 추출 실패 시 fallback)."""
    if not name:
        return None
    lower = name.lower()
    brand_map = {
        "nike": "Nike",
        "adidas": "Adidas",
        "puma": "Puma",
        "mizuno": "Mizuno",
        "new balance": "New Balance",
        "asics": "Asics",
        "umbro": "Umbro",
        "diadora": "Diadora",
    }
    for key, canonical in brand_map.items():
        if key in lower:
            return canonical
    return None


def _normalize_date(raw: str | None) -> str | None:
    """발매일 문자열을 YYYY-MM-DD 형태로 정규화 (실패 시 원본 반환)."""
    if not raw:
        return None
    match = re.search(r"(\d{4})[.\-/](\d{1,2})[.\-/](\d{1,2})", raw)
    if match:
        y, m, d = match.groups()
        return f"{y}-{int(m):02d}-{int(d):02d}"
    return raw
