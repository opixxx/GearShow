"""CLI 진입점 — sitemap → product → normalize → JSON export.

ADR-017: --category boots / uniform 두 카테고리 지원. 통계 집계는
normalizer.compute_match_stats 에 위임하여 cli 가 dict 키 형태에 결합되지 않도록 분리.
"""

from __future__ import annotations

import argparse
import logging
import sys
from collections.abc import Callable
from pathlib import Path

from kream_crawler.exporter import export
from kream_crawler.http_client import (
    CrawlerBlockedError,
    ForbiddenPathError,
    KreamClient,
)
from kream_crawler.normalizer import (
    CatalogItem,
    compute_match_stats,
    load_brands,
    load_clubs,
    load_silos,
    to_boots_item,
    to_uniform_item,
)
from kream_crawler.product_parser import parse_product_html
from kream_crawler.sitemap import (
    discover_boots_product_urls,
    discover_uniform_product_urls,
)

LOGGER = logging.getLogger("kream_crawler")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="kream-crawler",
        description="Kream 카탈로그 크롤러 — GearShow bulk-import 호환 JSON export",
    )
    parser.add_argument(
        "--category",
        choices=["boots", "uniform"],
        required=True,
        help="크롤링할 카테고리 — boots / uniform (ADR-017).",
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

    try:
        if args.category == "uniform":
            return _run_uniform(args)
        return _run_boots(args)
    except CrawlerBlockedError as exc:
        LOGGER.error("크롤러 차단됨 — 운영자 확인 필요: %s", exc)
        return 2


def _run_boots(args: argparse.Namespace) -> int:
    silos = load_silos()
    LOGGER.info("로드한 사일로: %d", len(silos))

    def normalize(raw: dict) -> CatalogItem:
        return to_boots_item(raw, silos)

    return _run_pipeline(
        args,
        category="BOOTS",
        discover=discover_boots_product_urls,
        normalize=normalize,
        unmatched_label="사일로 매칭 실패 — silos.yaml 보강 가이드:",
        unmatched_predicate=lambda item: not (item.get("bootsSpec") or {}).get("siloName"),
    )


def _run_uniform(args: argparse.Namespace) -> int:
    clubs = load_clubs()
    brands = load_brands()
    LOGGER.info("로드한 사전: clubs=%d, brands=%d", len(clubs), len(brands))

    def normalize(raw: dict) -> CatalogItem:
        return to_uniform_item(raw, clubs, brands)

    return _run_pipeline(
        args,
        category="UNIFORM",
        discover=discover_uniform_product_urls,
        normalize=normalize,
        unmatched_label="클럽 매칭 실패 — clubs.yaml 보강 가이드:",
        unmatched_predicate=lambda item: not (item.get("uniformSpec") or {}).get("clubName"),
    )


def _run_pipeline(
    args: argparse.Namespace,
    *,
    category: str,
    discover: Callable[[KreamClient, int], list[str]],
    normalize: Callable[[dict], CatalogItem],
    unmatched_label: str,
    unmatched_predicate: Callable[[CatalogItem], bool],
) -> int:
    """축구화/유니폼 공통 파이프라인 — discover → fetch → parse → normalize → export."""
    client = KreamClient(rate_limit_per_sec=args.rate_limit)

    LOGGER.info("[%s] 검색 endpoint 으로 상품 URL 수집 중...", category)
    candidate_urls = discover(client, args.limit)
    LOGGER.info("[%s] 후보 URL: %d", category, len(candidate_urls))

    items: list[CatalogItem] = []
    unmatched: list[str] = []
    fetched = 0
    parse_failed = 0

    try:
        for url in candidate_urls:
            if len(items) >= args.limit:
                break
            try:
                response = client.get(url)
            except (CrawlerBlockedError, ForbiddenPathError):
                # 정책 위반 — 부분 결과 dump 후 즉시 상위로 전파 (PR-B: code-reviewer Major #3).
                # 차단 응답을 만나도 다음 URL 로 넘어가면 추가 요청으로 차단이 가중됨.
                # 30개 중 25개 처리 후 차단 시점까지 모은 items 가 영구 유실되는 것을 막는다.
                _dump_partial(items, args.output, category)
                raise
            except Exception as exc:  # noqa: BLE001 - 일시적 네트워크/파싱 실패만 흡수
                LOGGER.warning("상품 페이지 fetch 실패 — url=%s, error=%s", url, exc)
                parse_failed += 1
                continue
            if response.status_code != 200:
                parse_failed += 1
                continue
            fetched += 1

            raw = parse_product_html(response.text, source_url=url)
            item = normalize(raw)
            items.append(item)

            if unmatched_predicate(item):
                unmatched.append(raw.get("name") or raw.get("name_ko") or url)
    except Exception:
        # 예상치 못한 에러도 부분 결과 보존
        _dump_partial(items, args.output, category)
        raise

    match_stats = compute_match_stats(items)
    stats = {
        "category": category,
        "candidateUrls": len(candidate_urls),
        "fetched": fetched,
        "parseFailed": parse_failed,
        **match_stats._asdict(),
    }

    export(items, args.output, stats=stats)
    LOGGER.info("[%s] export 완료 → %s (items=%d)", category, args.output, len(items))
    LOGGER.info(
        "[%s] 매칭률: silo=%d/%d, club=%d/%d, season=%d/%d, kitType=%d/%d, brand=%d/%d, koFullName=%d/%d",
        category,
        match_stats.silo_matched, match_stats.total,
        match_stats.club_matched, match_stats.total,
        match_stats.season_extracted, match_stats.total,
        match_stats.kit_type_inferred, match_stats.total,
        match_stats.brand_matched, match_stats.total,
        match_stats.korean_full_name_present, match_stats.total,
    )

    if unmatched:
        LOGGER.warning(unmatched_label)
        for name in unmatched[:20]:
            LOGGER.warning("  - %s", name)

    return 0


def _dump_partial(items: list, output_path, category: str) -> None:
    """부분 결과 보존 — fatal 예외 propagate 전 그때까지 모은 items 를 .partial.json 으로 dump.

    items 가 비어있으면 dump 하지 않는다 (빈 파일 노이즈 방지). 운영자는 .partial.json 을
    검수 후 정식 import 또는 재크롤링 결정.
    """
    if not items:
        return
    partial_path = output_path.with_suffix(".partial.json")
    export(items, partial_path, stats={
        "partial": True,
        "category": category,
        "items": len(items),
    })
    LOGGER.warning("부분 결과 저장 → %s (%d 건)", partial_path, len(items))


def _configure_logging(verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        stream=sys.stderr,
    )


if __name__ == "__main__":
    sys.exit(main())
