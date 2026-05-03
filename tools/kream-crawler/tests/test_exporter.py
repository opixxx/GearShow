"""Exporter 단위 테스트 — JSON 형식 + bulk-import API 호환성."""

from __future__ import annotations

import json
from pathlib import Path

from kream_crawler.exporter import export
from kream_crawler.normalizer import CatalogItem


def test_export_creates_bulk_import_compatible_json(tmp_path: Path):
    items: list[CatalogItem] = [
        {
            "category": "BOOTS",
            "brand": "Nike",
            "modelCode": "DJ4977-001",
            "officialImageUrl": "https://kream-phinf.pstatic.net/sample.jpg",
            "bootsSpec": {
                "studType": "FG",
                "siloName": "Mercurial Superfly",
                "releaseYear": "2024",
                "surfaceType": "천연잔디",
                "extraSpecJson": None,
            },
            "uniformSpec": None,
        }
    ]
    output = tmp_path / "out.json"

    export(items, output, stats={"items": 1})

    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["source"] == "kream"
    assert "crawledAt" in payload
    assert payload["stats"] == {"items": 1}
    assert payload["items"] == items


def test_export_items_field_matches_bulk_import_request(tmp_path: Path):
    """exporter 의 items 배열이 BulkImportCatalogItemRequest.items 와 동일 형식.

    운영자가 jq '{items}' 만으로 그대로 POST 가능해야 한다.
    """
    items: list[CatalogItem] = [
        {
            "category": "UNIFORM",
            "brand": "Nike",
            "modelCode": None,
            "officialImageUrl": None,
            "bootsSpec": None,
            "uniformSpec": None,
        }
    ]
    output = tmp_path / "out.json"
    export(items, output)

    payload = json.loads(output.read_text(encoding="utf-8"))
    bulk_request_body = {"items": payload["items"]}
    # 백엔드 BulkImportCatalogItemRequest 의 필수 필드 형태 확인
    assert isinstance(bulk_request_body["items"], list)
    assert "category" in bulk_request_body["items"][0]
    assert "brand" in bulk_request_body["items"][0]


def test_export_korean_aliases_round_trip(tmp_path: Path):
    """ADR-016 신규 필드 (fullNameKo/En + siloNameKo + clubNameKo + kitType nullable) round-trip."""
    items: list[CatalogItem] = [
        {
            "category": "BOOTS",
            "brand": "Nike",
            "modelCode": "AT5889-174",
            "officialImageUrl": None,
            "fullNameKo": "나이키 머큐리얼 슈퍼플라이",
            "fullNameEn": "Nike Mercurial Superfly",
            "bootsSpec": {
                "studType": "MG",
                "siloName": "Mercurial Superfly",
                "siloNameKo": "머큐리얼 슈퍼플라이",
                "releaseYear": "2024",
                "surfaceType": "혼합 잔디",
                "extraSpecJson": None,
            },
            "uniformSpec": None,
        },
        {
            "category": "UNIFORM",
            "brand": "Adidas",
            "modelCode": "VINTAGE-MUFC-8890",
            "officialImageUrl": None,
            "fullNameKo": "아디다스 맨체스터 유나이티드 1988/90",
            "fullNameEn": "Adidas Manchester United 1988/90",
            "bootsSpec": None,
            "uniformSpec": {
                "clubName": "Manchester United",
                "clubNameKo": "맨체스터 유나이티드",
                "season": "1988/90",
                "league": "EPL",
                "kitType": None,  # 빈티지 — ADR-016 §D3
                "extraSpecJson": None,
            },
        },
    ]
    output = tmp_path / "round-trip.json"
    export(items, output)

    payload = json.loads(output.read_text(encoding="utf-8"))
    assert payload["items"][0]["fullNameKo"] == "나이키 머큐리얼 슈퍼플라이"
    assert payload["items"][0]["bootsSpec"]["studType"] == "MG"
    assert payload["items"][0]["bootsSpec"]["siloNameKo"] == "머큐리얼 슈퍼플라이"
    assert payload["items"][1]["uniformSpec"]["clubNameKo"] == "맨체스터 유나이티드"
    assert payload["items"][1]["uniformSpec"]["kitType"] is None
    assert payload["items"][1]["uniformSpec"]["league"] == "EPL"
