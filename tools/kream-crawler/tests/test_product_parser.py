"""Product parser 단위 테스트 — fixture HTML 기반."""

from __future__ import annotations

from pathlib import Path

from kream_crawler.product_parser import is_boots_product, parse_product_html

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
