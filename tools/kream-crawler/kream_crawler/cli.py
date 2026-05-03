"""CLI 진입점 — sitemap → product → normalize → JSON export."""

from __future__ import annotations

import argparse
import logging
import sys
from pathlib import Path

from kream_crawler.exporter import export
from kream_crawler.http_client import CrawlerBlockedError, KreamClient
from kream_crawler.normalizer import (
    CatalogItem,
    load_silos,
    to_bulk_import_item,
)
from kream_crawler.product_parser import parse_product_html
from kream_crawler.sitemap import discover_boots_product_urls

LOGGER = logging.getLogger("kream_crawler")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="kream-crawler",
        description="Kream 카탈로그 크롤러 — GearShow bulk-import 호환 JSON export",
    )
    parser.add_argument(
        "--category",
        choices=["boots"],
        required=True,
        help="크롤링할 카테고리. 본 PR 은 boots 만 지원 (uniform 은 후속 PR).",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=30,
        help="최대 추출 상품 수 (기본 30).",
    )
    parser.add_argument(
        "--output",
        type=Path,
        required=True,
        help="JSON 출력 경로.",
    )
    parser.add_argument(
        "--rate-limit",
        type=float,
        default=1.0,
        help="초당 요청 수 (기본 1.0).",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="DEBUG 로그 출력.",
    )

    args = parser.parse_args(argv)
    _configure_logging(args.verbose)

    if args.category != "boots":
        raise NotImplementedError("uniform 카테고리는 후속 PR 에서 지원합니다.")

    try:
        return _run_boots(args)
    except CrawlerBlockedError as exc:
        LOGGER.error("크롤러 차단됨 — 운영자 확인 필요: %s", exc)
        return 2


def _run_boots(args: argparse.Namespace) -> int:
    client = KreamClient(rate_limit_per_sec=args.rate_limit)
    silos = load_silos()
    LOGGER.info("로드한 사일로: %d", len(silos))

    LOGGER.info("sitemap 으로 상품 URL 수집 중...")
    candidate_urls = discover_boots_product_urls(client, limit=args.limit)
    LOGGER.info("후보 URL: %d", len(candidate_urls))

    items: list[CatalogItem] = []
    silo_unmatched: list[str] = []
    fetched = 0
    parse_failed = 0

    for url in candidate_urls:
        if len(items) >= args.limit:
            break
        try:
            response = client.get(url)
        except Exception as exc:  # noqa: BLE001 - crawler 전반 안전성 우선
            LOGGER.warning("상품 페이지 fetch 실패 — url=%s, error=%s", url, exc)
            parse_failed += 1
            continue
        if response.status_code != 200:
            parse_failed += 1
            continue
        fetched += 1

        raw = parse_product_html(response.text, source_url=url)
        # Kream 상품 페이지는 SSR HTML 에 카테고리 정보가 노출되지 않으므로
        # 검색 keyword="축구화" 결과를 신뢰. 비축구화 항목은 운영자 검수에서 제거.
        item = to_bulk_import_item(raw, silos)
        items.append(item)

        if not item.get("bootsSpec", {}).get("siloName"):
            silo_unmatched.append(raw.get("name") or url)

    stud_matched = sum(
        1 for it in items if it.get("bootsSpec", {}).get("studType")
    )
    silo_matched = sum(
        1 for it in items if it.get("bootsSpec", {}).get("siloName")
    )

    stats = {
        "candidateUrls": len(candidate_urls),
        "fetched": fetched,
        "parseFailed": parse_failed,
        "items": len(items),
        "siloMatched": silo_matched,
        "studMatched": stud_matched,
    }

    export(items, args.output, stats=stats)
    LOGGER.info("export 완료 → %s (items=%d)", args.output, len(items))
    LOGGER.info(
        "매칭률: silo %d/%d, studType %d/%d",
        silo_matched, len(items), stud_matched, len(items),
    )

    if silo_unmatched:
        LOGGER.warning("사일로 매칭 실패 — silos.yaml 보강 가이드:")
        for name in silo_unmatched[:20]:
            LOGGER.warning("  - %s", name)

    return 0


def _configure_logging(verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        stream=sys.stderr,
    )


if __name__ == "__main__":
    sys.exit(main())
