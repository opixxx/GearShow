"""Raw 상품 dict → GearShow BulkImportCatalogItemRequest item dict 변환."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from importlib import resources
from typing import TypedDict

import yaml

from kream_crawler.product_parser import RawProduct

LOGGER = logging.getLogger(__name__)

STUD_PATTERN = re.compile(r"\b(FG|SG|AG|TF|IC|MG)\b", re.IGNORECASE)

# StudType → 한국어 surface 추론. 풋살화는 PR #71 사용자 결정에 따라 BOOTS+TF 로 매핑.
SURFACE_BY_STUD = {
    "FG": "천연잔디",
    "SG": "부드러운 천연잔디",
    "AG": "인조잔디",
    "TF": "짧은 인조잔디",
    "IC": "실내 코트",
    "MG": "혼합 잔디",
}


@dataclass(frozen=True)
class Silo:
    canonical: str
    brand: str
    aliases: tuple[str, ...]


class BootsSpecItem(TypedDict, total=False):
    studType: str | None
    siloName: str | None
    releaseYear: str | None
    surfaceType: str | None
    extraSpecJson: str | None


class CatalogItem(TypedDict, total=False):
    category: str
    brand: str | None
    modelCode: str | None
    officialImageUrl: str | None
    bootsSpec: BootsSpecItem | None
    uniformSpec: dict | None


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


def match_silo(name: str | None, silos: list[Silo]) -> Silo | None:
    """상품명에 사일로의 alias 가 포함되면 해당 Silo 반환 (가장 긴 매칭 우선)."""
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
    matches.sort(key=lambda x: -x[0])
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


def to_bulk_import_item(raw: RawProduct, silos: list[Silo]) -> CatalogItem:
    """raw 상품 dict 를 GearShow bulk-import API 의 단일 item 형식으로 변환.

    축구화만 지원 (본 PR 스코프). 유니폼은 후속 PR.
    silo/studType 매칭 실패 시 None 으로 두어 운영자 검수에 위임.
    """
    name = raw.get("name") or raw.get("name_ko") or ""
    silo = match_silo(name, silos)
    stud_type = extract_stud_type(name)

    # silo 매칭 결과의 brand 가 더 정확하므로 우선 사용 (raw.brand 가 잡지 못한 케이스 보강).
    brand = (silo.brand if silo else None) or raw.get("brand")

    return CatalogItem(
        category="BOOTS",
        brand=brand,
        modelCode=raw.get("style_code"),
        officialImageUrl=raw.get("image_url"),
        bootsSpec=BootsSpecItem(
            studType=stud_type,
            siloName=silo.canonical if silo else None,
            releaseYear=extract_release_year(raw.get("release_date")),
            surfaceType=infer_surface_type(stud_type),
            extraSpecJson=None,
        ),
        uniformSpec=None,
    )
