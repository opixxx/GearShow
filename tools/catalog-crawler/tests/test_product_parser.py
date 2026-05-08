"""Product parser 단위 테스트 — fixture HTML 기반."""

from __future__ import annotations

from pathlib import Path

from catalog_crawler.product_parser import (
    is_boots_product,
    parse_keywords,
    parse_product_html,
)

FIXTURE_DIR = Path(__file__).parent / "fixtures"


def _load(name: str) -> str:
    return (FIXTURE_DIR / name).read_text(encoding="utf-8")


def test_parse_extracts_og_meta():
    html = _load("sample_boots_product.html")
    raw = parse_product_html(html, source_url="https://kream.co.kr/products/123")
    assert raw["name"] == "Nike Mercurial Superfly 9 Elite FG Black"
    assert raw["image_url"] == "https://kream-phinf.pstatic.net/sample.jpg"
    assert raw["source_url"] == "https://kream.co.kr/products/123"


def test_parse_extracts_style_code_and_release_date():
    html = _load("sample_boots_product.html")
    raw = parse_product_html(html)
    assert raw["style_code"] == "DJ4977-001"
    assert raw["release_date"] == "2024-08-15"


def test_parse_brand_fallback_from_name():
    html = '<html><head><meta property="og:title" content="Adidas Predator Pro FG"/></head><body></body></html>'
    raw = parse_product_html(html)
    assert raw["brand"] == "Adidas"


def test_is_boots_product_detects_korean():
    raw = {"name": "축구화 모델 X", "category_path": "신발 > 축구화"}
    assert is_boots_product(raw) is True


def test_is_boots_product_detects_english():
    raw = {"name": "Football Boots Pro", "category_path": "Shoes > Football"}
    assert is_boots_product(raw) is True


def test_is_boots_product_rejects_other():
    raw = {"name": "운동화 SE", "category_path": "신발 > 스니커즈"}
    assert is_boots_product(raw) is False


# ===== ADR-017 §D1: keywords 메타 파싱 =====


def test_parse_keywords_three_tokens():
    keywords = "AT5889-174,나이키 프리미어 3 FG 화이트 메탈릭 골드,Nike Premier 3 FG White Metallic Gold"
    model_code, name_ko, name_en = parse_keywords(keywords)
    assert model_code == "AT5889-174"
    assert name_ko == "나이키 프리미어 3 FG 화이트 메탈릭 골드"
    assert name_en == "Nike Premier 3 FG White Metallic Gold"


def test_parse_keywords_with_extra_whitespace():
    keywords = "AT5889-174 , 나이키 프리미어 , Nike Premier"
    model_code, name_ko, name_en = parse_keywords(keywords)
    assert model_code == "AT5889-174"
    assert name_ko == "나이키 프리미어"
    assert name_en == "Nike Premier"


def test_parse_keywords_one_token_returns_none():
    """1-token 만 있으면 modelCode 추정 금지 — 오추론보다 누락이 안전."""
    model_code, name_ko, name_en = parse_keywords("AT5889-174")
    assert model_code is None
    assert name_ko is None
    assert name_en is None


def test_parse_keywords_empty_returns_none():
    assert parse_keywords("") == (None, None, None)
    assert parse_keywords(None) == (None, None, None)


def test_parse_keywords_with_empty_token_at_position():
    """3-token 중 일부가 빈 문자열이어도 위치는 유지, 빈 값은 None."""
    model_code, name_ko, name_en = parse_keywords("AT5889-174,,Nike Premier")
    assert model_code == "AT5889-174"
    assert name_ko is None
    assert name_en == "Nike Premier"


def test_parse_extracts_korean_and_english_names_from_keywords():
    html = _load("sample_boots_product.html")
    raw = parse_product_html(html)
    assert raw["name_ko"] == "나이키 머큐리얼 슈퍼플라이 9 엘리트 FG 블랙"
    assert raw["name_en"] == "Nike Mercurial Superfly 9 Elite FG Black"


def test_keywords_model_code_takes_precedence_over_jsonld():
    """ADR-017 §D1: keywords 의 modelCode 가 우선, JSON-LD sku 는 fallback."""
    html = _load("sample_boots_product.html")
    raw = parse_product_html(html)
    # fixture 의 keywords 첫 토큰은 DJ4977-001 (JSON-LD sku 와 동일)
    assert raw["style_code"] == "DJ4977-001"
