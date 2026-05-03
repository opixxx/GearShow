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
