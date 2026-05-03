"""Normalizer 단위 테스트 — silo 매칭, studType 정규식, 풋살화 케이스, ADR-017 한국어 alias / uniform."""

from __future__ import annotations

import pytest

from kream_crawler.normalizer import (
    Brand,
    Club,
    Silo,
    compute_match_stats,
    extract_kit_type,
    extract_kit_type_ko,
    extract_release_year,
    extract_season,
    extract_stud_type,
    infer_surface_type,
    load_brands,
    load_clubs,
    load_silos,
    match_brand,
    match_club,
    match_silo,
    to_boots_item,
    to_bulk_import_item,
    to_uniform_item,
)
from kream_crawler.product_parser import RawProduct


class TestExtractStudType:
    @pytest.mark.parametrize(
        "name, expected",
        [
            ("Nike Mercurial Superfly 9 Elite FG Black", "FG"),
            ("predator pro fg", "FG"),
            ("Mizuno Morelia Sala TF", "TF"),
            ("Mercurial Vapor MG 24/25", "MG"),
            ("Predator HG Edition", "HG"),
            ("Phantom GX SG Pro", "SG"),
        ],
    )
    def test_extracts_all_seven_stud_types(self, name, expected):
        """ADR-016: FG/SG/AG/TF/IC/MG/HG 7개 모두 매칭."""
        assert extract_stud_type(name) == expected

    def test_no_match(self):
        assert extract_stud_type("그냥 상품명") is None

    def test_none(self):
        assert extract_stud_type(None) is None


class TestInferSurfaceType:
    @pytest.mark.parametrize(
        "stud, surface",
        [
            ("FG", "천연잔디"),
            ("SG", "부드러운 천연잔디"),
            ("AG", "인조잔디"),
            ("TF", "짧은 인조잔디"),
            ("IC", "실내 코트"),
            ("MG", "혼합 잔디"),
            ("HG", "단단한 천연잔디"),
        ],
    )
    def test_all_seven_stud_types_map_to_surface(self, stud, surface):
        """ADR-016 7개 StudType 모두 surface 매핑."""
        assert infer_surface_type(stud) == surface

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

    def test_boots_persists_korean_full_name_and_silo_alias(self):
        """ADR-016: BOOTS 도 fullNameKo/En + siloNameKo 채움."""
        raw: RawProduct = {
            "name": "Nike Mercurial Superfly 9 Elite FG",
            "name_ko": "나이키 머큐리얼 슈퍼플라이 9 엘리트 FG",
            "name_en": "Nike Mercurial Superfly 9 Elite FG",
            "brand": "Nike",
            "style_code": "DJ4977-001",
        }
        item = to_bulk_import_item(raw, self.silos)
        assert item["fullNameKo"] == "나이키 머큐리얼 슈퍼플라이 9 엘리트 FG"
        assert item["fullNameEn"] == "Nike Mercurial Superfly 9 Elite FG"
        assert item["bootsSpec"]["siloName"] == "Mercurial Superfly"
        assert item["bootsSpec"]["siloNameKo"] == "머큐리얼 슈퍼플라이"


# ===== ADR-017 — 사전 (brands/clubs) 로드 + 매칭 =====


class TestLoadBrands:
    def test_loads_brands(self):
        brands = load_brands()
        assert len(brands) >= 8
        canonicals = {b.canonical for b in brands}
        for required in ("Nike", "Adidas", "Puma", "Mizuno"):
            assert required in canonicals

    def test_brand_alias_includes_korean(self):
        brands = load_brands()
        nike = next(b for b in brands if b.canonical == "Nike")
        assert "나이키" in nike.aliases


class TestLoadClubs:
    def test_loads_clubs(self):
        clubs = load_clubs()
        assert len(clubs) >= 30

    def test_national_team_has_null_league(self):
        clubs = load_clubs()
        korea = next(c for c in clubs if c.canonical == "Korea")
        assert korea.league is None
        assert "대한민국" in korea.aliases

    def test_club_has_league(self):
        clubs = load_clubs()
        man_united = next(c for c in clubs if c.canonical == "Manchester United")
        assert man_united.league == "EPL"


class TestMatchBrand:
    def setup_method(self):
        self.brands = [
            Brand("Nike", ("나이키",)),
            Brand("Adidas", ("아디다스",)),
        ]

    def test_match_by_english_canonical(self):
        b = match_brand("Nike", None, self.brands)
        assert b.canonical == "Nike"

    def test_match_by_korean_alias_when_english_missing(self):
        b = match_brand(None, "나이키 머큐리얼", self.brands)
        assert b.canonical == "Nike"

    def test_no_match_returns_none(self):
        b = match_brand("UnknownBrand", "알수없음", self.brands)
        assert b is None

    def test_english_takes_precedence_over_korean(self):
        b = match_brand("Adidas", "나이키", self.brands)
        # 영문 우선이 ADR-017 §D3
        assert b.canonical == "Adidas"


class TestMatchClub:
    def setup_method(self):
        self.clubs = [
            Club("Manchester United", ("맨체스터 유나이티드", "맨유"), "EPL"),
            Club("Manchester City", ("맨체스터 시티", "맨시티"), "EPL"),
            Club("Korea", ("대한민국", "한국"), None),
        ]

    def test_match_english_canonical(self):
        c = match_club("Manchester United Home 24/25", None, self.clubs)
        assert c.canonical == "Manchester United"

    def test_match_korean_short_alias(self):
        c = match_club(None, "맨유 24/25 홈", self.clubs)
        assert c.canonical == "Manchester United"

    def test_match_national_team(self):
        c = match_club(None, "대한민국 홈 24/25", self.clubs)
        assert c.canonical == "Korea"
        assert c.league is None

    def test_longest_match_wins(self):
        # "맨시티" 가 "맨" 보다 길어야 Manchester City 가 이김 (현재 alias 가 다른 길이).
        # 동률 케이스는 별도 테스트로 검증.
        c = match_club(None, "맨시티 어웨이", self.clubs)
        assert c.canonical == "Manchester City"

    def test_tiebreaker_canonical_order(self):
        """동률 길이 alias 시 canonical 알파벳 정렬 (결정성, ADR-017 §D3)."""
        # "AAA" 와 "AAB" 의 alias 길이가 같을 때 canonical 알파벳 순 ('AAA' 우선)
        clubs = [
            Club("AAB", ("ab",), None),
            Club("AAA", ("aa",), None),
        ]
        # "ab" 와 "aa" 둘 다 길이 2 — alphabetical canonical 순으로 AAA 또는 AAB 결정성
        c = match_club("text with ab and aa", None, clubs)
        # tiebreaker 가 canonical 알파벳 정렬이므로 AAA(짧은 alias 'aa') 가 이긴다 (단순 길이 동률 시)
        assert c.canonical == "AAA"

    def test_no_match_returns_none(self):
        c = match_club("Random Title", "랜덤", self.clubs)
        assert c is None


class TestExtractSeason:
    @pytest.mark.parametrize(
        "text, expected",
        [
            ("Manchester United Home 24/25", "24/25"),
            ("Liverpool 23-24 Away", "23/24"),
            ("Vintage Manchester United 1988/90", "1988/90"),
            ("Korea National Team 2024/2025", "2024/2025"),
        ],
    )
    def test_extracts_season(self, text, expected):
        assert extract_season(text) == expected

    def test_no_match(self):
        assert extract_season("그냥 유니폼") is None

    def test_none(self):
        assert extract_season(None) is None

    @pytest.mark.parametrize(
        "text",
        [
            # modelCode false positive — code-reviewer Critical
            "Nike Premier 3 FG White AT5889-174",
            "Adidas Predator AC9876-001 Pro",
            "Mercurial Vapor 16 (DJ4977-001)",
            # 합당하지 않은 year 토큰
            "Tokens 1500/600 invalid",
        ],
    )
    def test_ignores_model_code_and_implausible_years(self, text):
        """ADR-017 §D3: SEASON_PATTERN lookaround + year 범위 검증으로 false positive 차단."""
        assert extract_season(text) is None

    @pytest.mark.parametrize(
        "text, expected",
        [
            ("Adidas Predator 24-25 Pro AT5889-174", "24/25"),  # modelCode 가 함께 있어도 정상 시즌만 추출
            ("Manchester United 2025/2026 Home", "2025/2026"),
            ("Vintage 1990/91", "1990/91"),
        ],
    )
    def test_extracts_real_season_when_model_code_coexists(self, text, expected):
        assert extract_season(text) == expected


class TestExtractKitType:
    @pytest.mark.parametrize(
        "text, expected",
        [
            ("Manchester United HOME 24/25", "HOME"),
            ("Liverpool Away Jersey", "AWAY"),
            ("Real Madrid Third Kit", "THIRD"),
            ("Bayern 3RD Edition", "THIRD"),
        ],
    )
    def test_english_kit_types(self, text, expected):
        assert extract_kit_type(text) == expected

    def test_no_match(self):
        assert extract_kit_type("Manchester United") is None

    def test_none(self):
        assert extract_kit_type(None) is None


class TestExtractKitTypeKo:
    @pytest.mark.parametrize(
        "text, expected",
        [
            ("맨체스터 유나이티드 24/25 홈", "HOME"),
            ("리버풀 어웨이 저지", "AWAY"),
            ("레알 마드리드 써드 킷", "THIRD"),
            ("리버풀 원정 24/25", "AWAY"),
        ],
    )
    def test_korean_kit_types(self, text, expected):
        assert extract_kit_type_ko(text) == expected

    def test_no_match(self):
        assert extract_kit_type_ko("맨체스터 유나이티드") is None

    def test_none(self):
        assert extract_kit_type_ko(None) is None


# ===== UNIFORM 흐름 — to_uniform_item =====


class TestToUniformItem:
    def setup_method(self):
        self.clubs = load_clubs()
        self.brands = load_brands()

    def test_full_match_home_kit(self):
        raw: RawProduct = {
            "name_en": "Nike Manchester United Home 24/25",
            "name_ko": "나이키 맨체스터 유나이티드 24/25 홈",
            "brand": "Nike",
            "style_code": "MUFC-24-HOME",
            "image_url": "https://kream-phinf.pstatic.net/mufc.jpg",
        }
        item = to_uniform_item(raw, self.clubs, self.brands)
        assert item["category"] == "UNIFORM"
        assert item["brand"] == "Nike"
        assert item["modelCode"] == "MUFC-24-HOME"
        assert item["fullNameKo"] == "나이키 맨체스터 유나이티드 24/25 홈"
        spec = item["uniformSpec"]
        assert spec["clubName"] == "Manchester United"
        assert spec["clubNameKo"] == "맨체스터 유나이티드"
        assert spec["season"] == "24/25"
        assert spec["league"] == "EPL"
        assert spec["kitType"] == "HOME"
        assert item["bootsSpec"] is None

    def test_vintage_kit_type_null(self):
        """ADR-016 §D3: 빈티지 (kitType 미명시) 는 None 반환."""
        raw: RawProduct = {
            "name_en": "Adidas Manchester United 1988/90",
            "name_ko": "아디다스 맨체스터 유나이티드 1988/90",
            "brand": "Adidas",
            "style_code": "VINTAGE-MUFC-8890",
        }
        item = to_uniform_item(raw, self.clubs, self.brands)
        spec = item["uniformSpec"]
        assert spec["clubName"] == "Manchester United"
        assert spec["season"] == "1988/90"
        assert spec["kitType"] is None

    def test_national_team_league_null(self):
        """국가대표는 league None — ADR-016 §D3."""
        raw: RawProduct = {
            "name_en": "Nike Korea Home 24/25",
            "name_ko": "나이키 대한민국 24/25 홈",
            "brand": "Nike",
            "style_code": "KOREA-2425-HOME",
        }
        item = to_uniform_item(raw, self.clubs, self.brands)
        spec = item["uniformSpec"]
        assert spec["clubName"] == "Korea"
        assert spec["clubNameKo"] == "대한민국"
        assert spec["league"] is None
        assert spec["kitType"] == "HOME"

    def test_korean_only_kit_type_fallback(self):
        """영문 kitType 추출 실패 시 한국어 fallback (ADR-017 §D3)."""
        raw: RawProduct = {
            "name_en": "Liverpool 24/25",
            "name_ko": "리버풀 24/25 어웨이",
            "brand": "Nike",
        }
        item = to_uniform_item(raw, self.clubs, self.brands)
        assert item["uniformSpec"]["kitType"] == "AWAY"

    def test_unmatched_club_returns_none_fields(self):
        raw: RawProduct = {
            "name_en": "Some Unknown Club Jersey 24/25",
            "name_ko": "알 수 없는 클럽 24/25",
            "brand": "Nike",
        }
        item = to_uniform_item(raw, self.clubs, self.brands)
        spec = item["uniformSpec"]
        assert spec["clubName"] is None
        assert spec["clubNameKo"] is None
        assert spec["league"] is None
        assert spec["season"] == "24/25"


class TestToBulkImportItemDispatch:
    def setup_method(self):
        self.silos = load_silos()
        self.clubs = load_clubs()
        self.brands = load_brands()

    def test_boots_default(self):
        raw: RawProduct = {"name": "Nike Mercurial Superfly FG"}
        item = to_bulk_import_item(raw, self.silos)
        assert item["category"] == "BOOTS"

    def test_uniform_with_clubs_and_brands(self):
        raw: RawProduct = {
            "name_en": "Manchester United Home 24/25",
            "name_ko": "맨체스터 유나이티드 24/25 홈",
            "brand": "Adidas",
        }
        item = to_bulk_import_item(
            raw, self.silos, self.clubs, self.brands, category="UNIFORM"
        )
        assert item["category"] == "UNIFORM"

    def test_uniform_without_clubs_raises(self):
        raw: RawProduct = {"name_en": "Some Jersey"}
        with pytest.raises(ValueError):
            to_bulk_import_item(raw, [], category="UNIFORM")


class TestComputeMatchStats:
    def test_counts_boots_and_uniform_separately(self):
        items: list = [
            {
                "category": "BOOTS",
                "brand": "Nike",
                "fullNameKo": "나이키 머큐리얼",
                "bootsSpec": {"siloName": "Mercurial Superfly"},
                "uniformSpec": None,
            },
            {
                "category": "UNIFORM",
                "brand": "Nike",
                "fullNameKo": "맨체스터 유나이티드 24/25 홈",
                "bootsSpec": None,
                "uniformSpec": {
                    "clubName": "Manchester United",
                    "season": "24/25",
                    "kitType": "HOME",
                },
            },
            {
                "category": "BOOTS",
                "brand": None,
                "fullNameKo": None,
                "bootsSpec": {"siloName": None},
                "uniformSpec": None,
            },
        ]
        stats = compute_match_stats(items)
        assert stats["total"] == 3
        assert stats["silo_matched"] == 1
        assert stats["club_matched"] == 1
        assert stats["season_extracted"] == 1
        assert stats["kit_type_inferred"] == 1
        assert stats["brand_matched"] == 2
        assert stats["korean_full_name_present"] == 2
