"""JSON exporter — GearShow bulk-import API 호환 형식.

운영자가 손검수 후 수동으로 admin 토큰과 함께 POST 한다 (자동 호출 금지 — 약관 리스크 완화).
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from catalog_crawler.normalizer import CatalogItem


def export(
    items: list[CatalogItem],
    output_path: Path,
    stats: dict | None = None,
) -> None:
    """items 를 JSON 파일로 저장.

    출력 형식:
    {
      "source": "external",
      "crawledAt": "...",
      "stats": {...},
      "items": [...]      # bulk-import API 의 BulkImportCatalogItemRequest.items 와 동일
    }

    ``source`` 값은 외부 brand 식별자 대신 일반 토큰("external") 을 사용한다 —
    ADR-021 §D7 보강. 출처 식별이 필요한 운영 절차(takedown 등) 는 운영자 수동 절차
    또는 후속 PR 의 ``originSite`` 필드로 처리.

    운영자 import 절차:
      jq '{items}' output.json | curl -X POST .../bulk-import \\
        -H "Authorization: Bearer $GEARSHOW_ADMIN_TOKEN" -H "Content-Type: application/json" -d @-
    """
    payload = {
        "source": "external",
        "crawledAt": datetime.now(timezone.utc).isoformat(),
        "stats": stats or {},
        "items": items,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
