"""Normalizer 단위 테스트 — silo 매칭, studType 정규식, 풋살화 케이스."""

from __future__ import annotations

from kream_crawler.normalizer import (
    Silo,
    extract_release_year,
    extract_stud_type,
    infer_surface_type,
    load_silos,
    match_silo,
    to_bulk_import_item,
)
from kream_crawler.product_parser import RawProduct


class TestExtractStudType:
    def test_fg(self):
        assert extract_stud_type("Nike Mercurial Superfly 9 Elite FG Black") == "FG"

    def test_lowercase(self):
        assert extract_stud_type("predator pro fg") == "FG"

    def test_tf_for_futsal(self):
        assert extract_stud_type("Mizuno Morelia Sala TF") == "TF"

    def test_no_match(self):
        assert extract_stud_type("그냥 상품명") is None

    def test_none(self):
        assert extract_stud_type(None) is None


class TestInferSurfaceType:
    def test_fg(self):
        assert infer_surface_type("FG") == "천연잔디"

    def test_tf_futsal(self):
        # PR #71 사용자 결정: 풋살화 = BOOTS + TF.
        assert infer_surface_type("TF") == "짧은 인조잔디"

    def test_unknown(self):
        assert infer_surface_type("XYZ") is None

    def test_none(self):
        assert infer_surface_type(None) is None


class TestMatchSilo:
    def setup_method(self):
        self.silos = [
            Silo("Mercurial Superfly", "Nike", ("mercurial superfly", "머큐리얼 슈퍼플라이")),
            Silo("Mercurial Vapor", "Nike", ("mercurial vapor", "머큐리얼 베이퍼")),
            Silo("Predator", "Adidas", ("predator", "프레데터")),
        ]

    def test_english(self):
        result = match_silo("Nike Mercurial Vapor 16 Elite FG", self.silos)
        assert result is not None
        assert result.canonical == "Mercurial Vapor"

    def test_korean(self):
        result = match_silo("나이키 머큐리얼 슈퍼플라이 9 엘리트 FG 블랙", self.silos)
        assert result is not None
        assert result.canonical == "Mercurial Superfly"

    def test_longest_match_wins(self):
        # "mercurial superfly" 가 "mercurial vapor" 보다 길지만 둘 다 alias 길이가 같은 경우
        # superfly 가 들어간 이름이면 superfly 가 매칭되어야 한다.
        result = match_silo("Mercurial Superfly Elite", self.silos)
        assert result.canonical == "Mercurial Superfly"

    def test_no_match(self):
        assert match_silo("알 수 없는 사일로 SE", self.silos) is None

    def test_none_name(self):
        assert match_silo(None, self.silos) is None


class TestExtractReleaseYear:
    def test_iso_date(self):
        assert extract_release_year("2024-08-15") == "2024"

    def test_korean_date(self):
        assert extract_release_year("2025년 3월 12일") == "2025"

    def test_none(self):
        assert extract_release_year(None) is None

    def test_no_year(self):
        assert extract_release_year("미정") is None


class TestLoadSilos:
    def test_loads_30_silos(self):
        silos = load_silos()
        assert len(silos) >= 30
        canonicals = {s.canonical for s in silos}
        # 핵심 사일로 필수
        assert "Mercurial Superfly" in canonicals
        assert "Predator" in canonicals
        assert "Morelia Neo" in canonicals


class TestToBulkImportItem:
    def setup_method(self):
        self.silos = load_silos()

    def test_full_match(self):
        raw: RawProduct = {
            "name": "Nike Mercurial Superfly 9 Elite FG Black",
            "brand": "Nike",
            "style_code": "DJ4977-001",
            "release_date": "2024-08-15",
            "image_url": "https://kream-phinf.pstatic.net/sample.jpg",
            "category_path": "신발 > 축구화",
        }
        item = to_bulk_import_item(raw, self.silos)
        assert item["category"] == "BOOTS"
        assert item["brand"] == "Nike"
        assert item["modelCode"] == "DJ4977-001"
        assert item["officialImageUrl"] == "https://kream-phinf.pstatic.net/sample.jpg"
        assert item["bootsSpec"]["studType"] == "FG"
        assert item["bootsSpec"]["siloName"] == "Mercurial Superfly"
        assert item["bootsSpec"]["releaseYear"] == "2024"
        assert item["bootsSpec"]["surfaceType"] == "천연잔디"
        assert item["uniformSpec"] is None

    def test_futsal_tf(self):
        # PR #71 사용자 결정: 풋살화 = BOOTS + StudType.TF.
        raw: RawProduct = {
            "name": "Mizuno Morelia Neo IV TF",
            "brand": "Mizuno",
            "style_code": "P1GD2270",
            "release_date": "2025-01-10",
            "image_url": None,
            "category_path": "축구화",
        }
        item = to_bulk_import_item(raw, self.silos)
        assert item["category"] == "BOOTS"
        assert item["bootsSpec"]["studType"] == "TF"
        assert item["bootsSpec"]["siloName"] == "Morelia Neo"
        assert item["bootsSpec"]["surfaceType"] == "짧은 인조잔디"

    def test_silo_unmatched_returns_none_silo(self):
        raw: RawProduct = {
            "name": "Unknown Brand Boot SE FG",
            "brand": None,
            "style_code": "UNK-001",
        }
        item = to_bulk_import_item(raw, self.silos)
        assert item["bootsSpec"]["siloName"] is None
        assert item["bootsSpec"]["studType"] == "FG"

    def test_silo_brand_overrides_raw_brand_when_raw_missing(self):
        raw: RawProduct = {
            "name": "Predator Pro FG",
            "brand": None,
        }
        item = to_bulk_import_item(raw, self.silos)
        assert item["brand"] == "Adidas"
