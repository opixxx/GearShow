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
from typing import NamedTuple, TypedDict

import yaml

from catalog_crawler.product_parser import RawProduct

LOGGER = logging.getLogger(__name__)

# 시즌 추출 정규식 — '24/25', '24-25', '1988/90' 모두 매칭 (ADR-017 §D3).
# lookaround 로 modelCode (예: AT5889-174) 의 dash 부분이 시즌으로 오인식되는 false positive 차단.
SEASON_PATTERN = re.compile(r"(?<![\w-])(\d{2,4})[/-](\d{2,4})(?![\w-])")

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

# 풀텍스트 → StudType 매핑 — Adidas/Mizuno 가 약어 대신 풀텍스트로 표기하는 케이스 보강.
# "AS" (All Surface) 는 Mizuno 풋살화 표기 — Q1 결정으로 TF 로 매핑 (ENUM 변경 없이 매핑 정책 만 추가).
# 순서 중요: 긴 매칭 우선 (예: "soft ground" 가 "ground" 보다 먼저 시도되도록 dict insertion order 의존 + .startswith 가 아닌 substring 검색).
_STUD_FULL_TEXT_MAP = {
    "firm ground": "FG",
    "soft ground": "SG",
    "artificial ground": "AG",
    "hard ground": "HG",
    "multi ground": "MG",
    "all surface": "TF",   # Mizuno 풋살화 → TF
    "indoor": "IC",
    "turf": "TF",
}

# AS 단독 토큰 (Mizuno 모델명의 " AS " 같은 표기) — TF 로 매핑.
_STUD_AS_PATTERN = re.compile(r"\bAS\b")

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
    text = resources.files("catalog_crawler.dictionaries").joinpath("silos.yaml").read_text(
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
    text = resources.files("catalog_crawler.dictionaries").joinpath("brands.yaml").read_text(
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
    text = resources.files("catalog_crawler.dictionaries").joinpath("clubs.yaml").read_text(
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
    """상품명 단일 haystack 에 사일로의 alias 가 포함되면 해당 Silo 반환.

    ADR-017 §D3 매칭 정책 — 단일 haystack:
    - longest alias match wins
    - 동률 시 canonical 알파벳 정렬 (결정성 보장)
    - {@code match_brand} 와 달리 영문/한국어 분리 처리 안 함 (사일로 alias 가 영/한 함께 yaml 에 등재되어 있어 단일 lower 검색이면 충분)
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
    """ADR-017 §D3 매칭 정책 — 영문 우선, 한국어 fallback (직렬):

    1. brand_en 의 canonical 정확 일치 시 즉시 반환 (영문 brand 가 1차 신호).
    2. 실패 시 name_ko 에서 한국어 alias longest match.
    3. 동률 시 canonical 알파벳 정렬 (결정성).

    {@code match_club} 과 비대칭인 이유: brand 는 영문이 운영 진실의 원천이며 (silos.yaml 의
    brand 필드, Kream 페이지의 BreadcrumbList) 한국어 alias 는 사용자 직접 입력 케이스에만
    등장하므로 영문 우선이 합리. 클럽은 영/한 둘 중 하나만 노출되는 케이스가 흔하다.
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
    """ADR-017 §D3 매칭 정책 — 영/한 양쪽 검색 (병렬):

    1. name_en 과 name_ko 두 haystack 모두에서 canonical / alias 매칭 시도.
    2. 모든 매칭 후보 중 longest alias match wins.
    3. 동률 시 canonical 알파벳 정렬 (결정성).

    {@code match_brand} 와 비대칭인 이유: 클럽명은 영/한 둘 중 하나만 노출되는 케이스가
    실측에 흔하다 ("Manchester United Home" / "맨유 24/25 홈" 둘 다 사용자 입력 가능).
    영문 우선 직렬로 처리하면 한국어만 있는 입력에서 매칭 실패. 양쪽 병렬 검색이 매칭률↑.
    """
    if not (name_en or name_ko):
        return None
    haystacks = [(name_en or "").lower(), (name_ko or "").lower()]
    matches: list[tuple[int, Club]] = []
    for club in clubs:
        canonical_lower = club.canonical.lower()
        # PR-B (code-reviewer Major #2): 운영자가 yaml 의 aliases 에 canonical 을
        # (실수 또는 의도적으로) 포함시키면 같은 club 이 matches 에 두 번 push 되어
        # 동률 형성으로 의도치 않은 우선순위 변동 발생. canonical 이 alias 와 중복되면
        # alias 매칭에 위임하고 별도 canonical 매칭 step 을 생략한다.
        canonical_in_aliases = canonical_lower in club.aliases
        for hay in haystacks:
            if not hay:
                continue
            if canonical_lower in hay and not canonical_in_aliases:
                matches.append((len(canonical_lower), club))
            for alias in club.aliases:
                if alias in hay:
                    matches.append((len(alias), club))
    if not matches:
        return None
    matches.sort(key=lambda x: (-x[0], x[1].canonical))
    return matches[0][1]


def extract_stud_type(name: str | None) -> str | None:
    """모델명에서 StudType 추출.

    우선순위:
    1. 약어 매칭 (STUD_PATTERN) — `\b(FG|SG|AG|TF|IC|MG|HG)\b`. 기존 정책 유지.
    2. 풀텍스트 매핑 (`_STUD_FULL_TEXT_MAP`) — "Firm Ground"/"All Surface" 등.
    3. AS 단독 토큰 — Mizuno 풋살화 표기 → TF.

    약어 우선순위는 의도적 — 모델명이 약어를 박는 경우가 표준이고, 풀텍스트는 fallback.
    """
    if not name:
        return None

    # 1. 약어 매칭 우선 (기존 동작 보존).
    match = STUD_PATTERN.search(name)
    if match:
        return match.group(1).upper()

    # 2. 풀텍스트 매핑 (case-insensitive).
    lower = name.lower()
    for full_text, stud in _STUD_FULL_TEXT_MAP.items():
        if full_text in lower:
            return stud

    # 3. AS 단독 토큰 → TF (Mizuno 풋살화, Q1 결정).
    if _STUD_AS_PATTERN.search(name):
        return "TF"

    return None


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
    """ADR-017 §D3 + 본 PR 보강: '24/25', '24-25', '1988/90' 또는 단일 4-digit 연도에서 시즌 추출.

    우선순위:
    1. SEASON_PATTERN (시즌 쌍, '24/25') — 기존 정책 유지, 가장 구체적.
    2. 단일 4-digit 연도 ('2026', '2024') fallback — 본 PR 보강.
       modelCode dash 패턴 (예: IB9007-436 의 9007) false positive 회피.
       1980 <= year <= 2099 범위만 허용 — 다른 4-digit 숫자 (모델번호 등) 차단.

    빈티지 4-digit 쌍은 그대로 보존 ('1988/90' → '1988/90').
    빈티지 2-digit 단독 ('91') 은 false positive 위험으로 미지원 — 운영자 수동 backfill 정책.
    """
    if not text:
        return None
    match = SEASON_PATTERN.search(text)
    if match:
        a, b = match.group(1), match.group(2)
        if _is_plausible_season_token(a) and _is_plausible_season_token(b):
            return f"{a}/{b}"

    # Fallback: 단일 4-digit 연도. modelCode 패턴 (대문자/숫자 인접) 회피.
    single_match = _SINGLE_YEAR_PATTERN.search(text)
    if single_match:
        year = int(single_match.group(1))
        if 1980 <= year <= 2099:
            return single_match.group(1)
    return None


# 단일 4-digit 연도 추출 정규식 — modelCode (예: IB9007-436) 의 4-digit 부분과
# 충돌 회피를 위해 양옆에 영문자/숫자/dash 가 인접하지 않은 경우만 매칭.
_SINGLE_YEAR_PATTERN = re.compile(r"(?<![A-Za-z0-9-])(\d{4})(?![\w-])")


def _is_plausible_season_token(token: str) -> bool:
    """시즌 토큰이 합당한 year 표기인지 검증 — 2-digit (00-99) 또는 4-digit (1900-2099)."""
    if len(token) == 2:
        return token.isdigit()
    if len(token) == 4:
        n = int(token)
        return 1900 <= n <= 2099
    return False


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


def _is_korean_char(ch: str) -> bool:
    """한국어 문자 판정 — 음절 + 자모 + 호환 자모 모두 포함 (PR-B 보강).

    이전 구현은 음절 (U+AC00-D7A3, "가-힣") 만 인식해서 자모 ("ㄱㄴㄷ") / 호환 자모를
    가진 alias (드물지만 yaml 보강 시 가능) 를 영문으로 오인. 본 함수는 세 범위 모두 인식.
    """
    code = ord(ch)
    return (
        0xAC00 <= code <= 0xD7A3       # 한글 음절 (가-힣)
        or 0x1100 <= code <= 0x11FF    # 한글 자모
        or 0x3130 <= code <= 0x318F    # 한글 호환 자모
    )


def _korean_char_count(s: str) -> int:
    return sum(1 for c in s if _is_korean_char(c))


def _extract_korean_alias(entry: Silo | Club | None) -> str | None:
    """alias 중 한글이 가장 많이 포함된 항목 반환 (없으면 None) — PR-B 보강.

    이전 구현은 yaml 순서의 첫 한글 alias 를 반환했다. 운영자가 yaml 갱신 시 영/한 순서를
    뒤집으면 의도치 않게 영문 prefix alias 가 한국어로 잡히거나, 첫 한국어 alias 가
    무시되는 fragile 동작. 본 구현은 한글 char count 기준으로 정렬하여 yaml 순서에 무관.

    동률 시 yaml 등재 순서 우선 (Python sort 가 stable). 한글 음절/자모/호환 자모 모두 인식.
    """
    if entry is None:
        return None
    # walrus 로 한 번의 list 생성 — 한글 char count 0 인 alias 제거 (PR-B 최적화).
    candidates = [(a, n) for a in entry.aliases if (n := _korean_char_count(a)) > 0]
    if not candidates:
        return None
    candidates.sort(key=lambda x: -x[1])  # 한글 가장 많은 alias 우선, 동률은 등재 순서
    return candidates[0][0]


class MatchStats(NamedTuple):
    """ADR-017 §D4 매칭률 통계 — typed contract (PR-B 보강).

    NamedTuple 로 표현하여 cli 가 dict 키 형태에 결합되지 않도록 attribute access 강제.
    IDE/mypy 가 키 변경을 즉시 감지. 기존 export schema 의 stats dict 호환은
    {@code stats._asdict()} 로 유지.

    **호환성 명세**: Python 3.7+ 부터 {@code _asdict()} 가 plain {@code dict}
    (insertion-ordered) 를 반환 — export JSON 의 stats 키 순서가 NamedTuple 필드 선언
    순서와 동일. 본 프로젝트는 Python 3.11+ (pyproject.toml `requires-python`) 라
    CPython/PyPy 3.11+ 모두 동일 동작.
    """
    total: int
    silo_matched: int
    club_matched: int
    season_extracted: int
    kit_type_inferred: int
    brand_matched: int
    korean_full_name_present: int


def compute_match_stats(items: list[CatalogItem]) -> MatchStats:
    """ADR-017 §D4 매칭률 측정 — cli 가 dict 키 형태에 결합되지 않도록 typed NamedTuple 반환.

    각 카운트:
    - total / silo_matched / club_matched / season_extracted / kit_type_inferred
    - brand_matched / korean_full_name_present
    """
    silo_matched = 0
    club_matched = 0
    season_extracted = 0
    kit_type_inferred = 0
    brand_matched = 0
    korean_full_name_present = 0
    for item in items:
        if item.get("brand"):
            brand_matched += 1
        if item.get("fullNameKo"):
            korean_full_name_present += 1
        boots = item.get("bootsSpec")
        if boots and boots.get("siloName"):
            silo_matched += 1
        uniform = item.get("uniformSpec")
        if uniform:
            if uniform.get("clubName"):
                club_matched += 1
            if uniform.get("season"):
                season_extracted += 1
            if uniform.get("kitType"):
                kit_type_inferred += 1
    return MatchStats(
        total=len(items),
        silo_matched=silo_matched,
        club_matched=club_matched,
        season_extracted=season_extracted,
        kit_type_inferred=kit_type_inferred,
        brand_matched=brand_matched,
        korean_full_name_present=korean_full_name_present,
    )
