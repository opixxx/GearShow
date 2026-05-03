"""Raw 상품 dict → GearShow BulkImportCatalogItemRequest item dict 변환.

Mirror of (백엔드 contract — 변경 시 본 모듈도 동기화):
- com.gearshow.backend.catalog.adapter.in.web.dto.BulkImportCatalogItemRequest.Item (PR #71)
- com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand fields (PR #75 ADR-016)
- com.gearshow.backend.catalog.domain.vo.StudType (FG/SG/AG/TF/IC/MG/HG — PR #75)
- com.gearshow.backend.catalog.domain.vo.KitType (HOME/AWAY/THIRD, nullable — PR #75 ADR-016)
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from importlib import resources
from typing import TypedDict

import yaml

from kream_crawler.product_parser import RawProduct

LOGGER = logging.getLogger(__name__)

# 시즌 추출 정규식 — '24/25', '24-25', '1988/90' 모두 매칭 (ADR-017 §D3).
SEASON_PATTERN = re.compile(r"(\d{2,4})[/-](\d{2,4})")

# 킷 타입 영/한 매핑 (ADR-016 KitType 정규값).
_KIT_TYPE_EN_PATTERN = re.compile(r"\b(HOME|AWAY|THIRD|3RD)\b", re.IGNORECASE)
_KIT_TYPE_KO_MAP = {
    "홈": "HOME",
    "어웨이": "AWAY",
    "원정": "AWAY",
    "써드": "THIRD",
    "서드": "THIRD",
}

# StudType 정규식 — ADR-016 의 7개 enum (FG/SG/AG/TF/IC/MG/HG) 모두 매칭.
STUD_PATTERN = re.compile(r"\b(FG|SG|AG|TF|IC|MG|HG)\b", re.IGNORECASE)

# StudType → 한국어 surface 추론. 풋살화는 PR #71 사용자 결정에 따라 BOOTS+TF 로 매핑.
SURFACE_BY_STUD = {
    "FG": "천연잔디",
    "SG": "부드러운 천연잔디",
    "AG": "인조잔디",
    "TF": "짧은 인조잔디",
    "IC": "실내 코트",
    "MG": "혼합 잔디",
    "HG": "단단한 천연잔디",
}


@dataclass(frozen=True)
class Silo:
    canonical: str
    brand: str
    aliases: tuple[str, ...]


@dataclass(frozen=True)
class Brand:
    """브랜드 정규화 사전 항목 (ADR-017 §D2)."""

    canonical: str
    aliases: tuple[str, ...]


@dataclass(frozen=True)
class Club:
    """클럽/국가대표 정규화 사전 항목 (ADR-017 §D2).

    league 가 None 이면 국가대표 (ADR-016 §D3 — 빈티지/국가대표는 league nullable).
    aliases[0] 은 한국어 풀네임으로 간주, clubNameKo 로 사용.
    """

    canonical: str
    aliases: tuple[str, ...]
    league: str | None


class BootsSpecItem(TypedDict, total=False):
    studType: str | None             # FG/SG/AG/TF/IC/MG/HG (PR #75 ADR-016)
    siloName: str | None
    siloNameKo: str | None           # 사일로 한국어 alias (PR #75 ADR-016)
    releaseYear: str | None
    surfaceType: str | None
    extraSpecJson: str | None


class UniformSpecItem(TypedDict, total=False):
    clubName: str | None
    clubNameKo: str | None           # 클럽 한국어 alias (PR #75 ADR-016)
    season: str | None
    league: str | None               # nullable — 국가대표는 None (PR #75 ADR-016)
    kitType: str | None              # nullable — 빈티지/추출 실패 모두 None (PR #75 ADR-016)
    extraSpecJson: str | None


class CatalogItem(TypedDict, total=False):
    category: str
    brand: str | None
    modelCode: str | None
    officialImageUrl: str | None
    fullNameKo: str | None           # 한국어 풀네임 (PR #75 ADR-016)
    fullNameEn: str | None           # 영문 풀네임 (PR #75 ADR-016)
    bootsSpec: BootsSpecItem | None
    uniformSpec: UniformSpecItem | None


def load_silos() -> list[Silo]:
    """패키지 내 silos.yaml 을 로드하여 Silo 리스트 반환."""
    text = resources.files("kream_crawler.dictionaries").joinpath("silos.yaml").read_text(
        encoding="utf-8"
    )
    raw = yaml.safe_load(text) or []
    return [
        Silo(
            canonical=entry["canonical"],
            brand=entry["brand"],
            aliases=tuple(a.lower() for a in entry["aliases"]),
        )
        for entry in raw
    ]


def load_brands() -> list[Brand]:
    """패키지 내 brands.yaml 을 로드하여 Brand 리스트 반환 (ADR-017 §D2)."""
    text = resources.files("kream_crawler.dictionaries").joinpath("brands.yaml").read_text(
        encoding="utf-8"
    )
    raw = yaml.safe_load(text) or []
    return [
        Brand(
            canonical=entry["canonical"],
            aliases=tuple(a.lower() for a in entry["aliases"]),
        )
        for entry in raw
    ]


def load_clubs() -> list[Club]:
    """패키지 내 clubs.yaml 을 로드하여 Club 리스트 반환 (ADR-017 §D2)."""
    text = resources.files("kream_crawler.dictionaries").joinpath("clubs.yaml").read_text(
        encoding="utf-8"
    )
    raw = yaml.safe_load(text) or []
    return [
        Club(
            canonical=entry["canonical"],
            aliases=tuple(a.lower() for a in entry["aliases"]),
            league=entry.get("league"),
        )
        for entry in raw
    ]


def match_silo(name: str | None, silos: list[Silo]) -> Silo | None:
    """상품명에 사일로의 alias 가 포함되면 해당 Silo 반환.

    ADR-017 §D3: longest alias match wins. 동률 시 canonical 알파벳 정렬 (결정성 보장).
    """
    if not name:
        return None
    lower = name.lower()
    matches: list[tuple[int, Silo]] = []
    for silo in silos:
        for alias in silo.aliases:
            if alias in lower:
                matches.append((len(alias), silo))
    if not matches:
        return None
    matches.sort(key=lambda x: (-x[0], x[1].canonical))
    return matches[0][1]


def match_brand(
    brand_en: str | None, name_ko: str | None, brands: list[Brand]
) -> Brand | None:
    """ADR-017 §D3: 영문 brand canonical 우선 매칭, 실패 시 한국어 alias 매칭.

    name_ko 매칭 시에도 longest match wins + canonical 알파벳 tiebreaker.
    """
    if brand_en:
        lower_en = brand_en.lower()
        for b in brands:
            if b.canonical.lower() == lower_en:
                return b
    if not name_ko:
        return None
    lower_ko = name_ko.lower()
    matches: list[tuple[int, Brand]] = []
    for b in brands:
        for alias in b.aliases:
            if alias in lower_ko:
                matches.append((len(alias), b))
    if not matches:
        return None
    matches.sort(key=lambda x: (-x[0], x[1].canonical))
    return matches[0][1]


def match_club(
    name_en: str | None, name_ko: str | None, clubs: list[Club]
) -> Club | None:
    """ADR-017 §D3: 영문 canonical / 한국어 alias 양쪽에서 longest match wins.

    동률 시 canonical 알파벳 정렬 (결정성). 양쪽 모두 매칭되면 더 긴 매칭 우선.
    """
    if not (name_en or name_ko):
        return None
    haystacks = [(name_en or "").lower(), (name_ko or "").lower()]
    matches: list[tuple[int, Club]] = []
    for club in clubs:
        canonical_lower = club.canonical.lower()
        for hay in haystacks:
            if not hay:
                continue
            if canonical_lower in hay:
                matches.append((len(canonical_lower), club))
            for alias in club.aliases:
                if alias in hay:
                    matches.append((len(alias), club))
    if not matches:
        return None
    matches.sort(key=lambda x: (-x[0], x[1].canonical))
    return matches[0][1]


def extract_stud_type(name: str | None) -> str | None:
    if not name:
        return None
    match = STUD_PATTERN.search(name)
    return match.group(1).upper() if match else None


def infer_surface_type(stud_type: str | None) -> str | None:
    if stud_type is None:
        return None
    return SURFACE_BY_STUD.get(stud_type)


def extract_release_year(release_date: str | None) -> str | None:
    if not release_date:
        return None
    match = re.search(r"(\d{4})", release_date)
    return match.group(1) if match else None


def extract_season(text: str | None) -> str | None:
    """ADR-017 §D3: '24/25', '24-25', '1988/90' 등에서 시즌 추출. 매칭 시 'a/b' 형식.

    빈티지 4-digit 도 그대로 보존 ('1988/90' → '1988/90').
    """
    if not text:
        return None
    match = SEASON_PATTERN.search(text)
    if not match:
        return None
    return f"{match.group(1)}/{match.group(2)}"


def extract_kit_type(text: str | None) -> str | None:
    """영문 텍스트에서 KitType 정규값 추출. HOME/AWAY/THIRD/3RD → HOME/AWAY/THIRD/THIRD.

    매칭 실패 시 None — 빈티지 (kitType 미명시) 케이스 ADR-016 §D3.
    """
    if not text:
        return None
    match = _KIT_TYPE_EN_PATTERN.search(text)
    if not match:
        return None
    raw = match.group(1).upper()
    return "THIRD" if raw == "3RD" else raw


def extract_kit_type_ko(text: str | None) -> str | None:
    """한국어 텍스트에서 KitType 정규값 추출. 홈/어웨이/써드 → HOME/AWAY/THIRD."""
    if not text:
        return None
    for ko, canonical in _KIT_TYPE_KO_MAP.items():
        if ko in text:
            return canonical
    return None


def to_boots_item(raw: RawProduct, silos: list[Silo]) -> CatalogItem:
    """raw 상품 dict → BOOTS CatalogItem.

    silo/studType 매칭 실패 시 None — 운영자 검수에 위임.
    """
    name_en = raw.get("name_en") or raw.get("name") or ""
    name_ko = raw.get("name_ko") or ""
    haystack = f"{name_en} {name_ko}".strip()

    silo = match_silo(haystack, silos)
    stud_type = extract_stud_type(haystack)

    # silo 매칭의 brand 가 더 정확 (raw.brand 가 못 잡은 케이스 보강).
    brand = (silo.brand if silo else None) or raw.get("brand")

    # silo 의 한국어 alias 추출 — alias 중 한글 첫 항목 (없으면 None).
    silo_name_ko = _extract_korean_alias(silo) if silo else None

    return CatalogItem(
        category="BOOTS",
        brand=brand,
        modelCode=raw.get("style_code"),
        officialImageUrl=raw.get("image_url"),
        fullNameKo=name_ko or None,
        fullNameEn=name_en or None,
        bootsSpec=BootsSpecItem(
            studType=stud_type,
            siloName=silo.canonical if silo else None,
            siloNameKo=silo_name_ko,
            releaseYear=extract_release_year(raw.get("release_date")),
            surfaceType=infer_surface_type(stud_type),
            extraSpecJson=None,
        ),
        uniformSpec=None,
    )


def to_uniform_item(
    raw: RawProduct, clubs: list[Club], brands: list[Brand]
) -> CatalogItem:
    """raw 상품 dict → UNIFORM CatalogItem (ADR-017 §D3).

    club/season/kitType 매칭 실패 시 None 으로 두어 운영자 검수 위임.
    빈티지 (kitType 미명시) / 국가대표 (league None) 모두 정상 케이스.
    """
    name_en = raw.get("name_en") or raw.get("name") or ""
    name_ko = raw.get("name_ko") or ""

    club = match_club(name_en, name_ko, clubs)
    brand = match_brand(raw.get("brand"), name_ko, brands)
    season = extract_season(name_en) or extract_season(name_ko)
    # 영문 우선, 영문 None 시 한국어 fallback (ADR-017 §D3).
    kit_type = extract_kit_type(name_en) or extract_kit_type_ko(name_ko)

    return CatalogItem(
        category="UNIFORM",
        brand=(brand.canonical if brand else None) or raw.get("brand"),
        modelCode=raw.get("style_code"),
        officialImageUrl=raw.get("image_url"),
        fullNameKo=name_ko or None,
        fullNameEn=name_en or None,
        bootsSpec=None,
        uniformSpec=UniformSpecItem(
            clubName=club.canonical if club else None,
            clubNameKo=_extract_korean_alias(club) if club else None,
            season=season,
            league=club.league if club else None,
            kitType=kit_type,
            extraSpecJson=None,
        ),
    )


def to_bulk_import_item(
    raw: RawProduct,
    silos: list[Silo],
    clubs: list[Club] | None = None,
    brands: list[Brand] | None = None,
    *,
    category: str = "BOOTS",
) -> CatalogItem:
    """raw 상품 dict → bulk-import API 의 단일 item.

    category 파라미터로 BOOTS / UNIFORM 분기. UNIFORM 시 clubs/brands 필수.
    """
    if category == "UNIFORM":
        if clubs is None or brands is None:
            raise ValueError("UNIFORM 카테고리는 clubs + brands 사전이 필요합니다")
        return to_uniform_item(raw, clubs, brands)
    return to_boots_item(raw, silos)


def _extract_korean_alias(entry: Silo | Club | None) -> str | None:
    """alias 들 중 한글 문자가 포함된 첫 항목 반환 (없으면 None).

    silos.yaml 은 alias 에 영문 lowercase 와 한국어를 함께 가지므로 한글만 골라낸다.
    """
    if entry is None:
        return None
    for alias in entry.aliases:
        if any("가" <= ch <= "힣" for ch in alias):
            return alias
    return None


def compute_match_stats(items: list[CatalogItem]) -> dict[str, int]:
    """ADR-017 §D4 매칭률 측정용 통계 — cli 가 normalizer 의 dict 키 형태에 결합되지 않도록 helper 분리.

    각 카운트:
    - total / silo_matched / club_matched / season_extracted / kit_type_inferred
    - brand_matched / korean_full_name_present
    """
    stats = {
        "total": len(items),
        "silo_matched": 0,
        "club_matched": 0,
        "season_extracted": 0,
        "kit_type_inferred": 0,
        "brand_matched": 0,
        "korean_full_name_present": 0,
    }
    for item in items:
        if item.get("brand"):
            stats["brand_matched"] += 1
        if item.get("fullNameKo"):
            stats["korean_full_name_present"] += 1
        boots = item.get("bootsSpec")
        if boots and boots.get("siloName"):
            stats["silo_matched"] += 1
        uniform = item.get("uniformSpec")
        if uniform:
            if uniform.get("clubName"):
                stats["club_matched"] += 1
            if uniform.get("season"):
                stats["season_extracted"] += 1
            if uniform.get("kitType"):
                stats["kit_type_inferred"] += 1
    return stats
