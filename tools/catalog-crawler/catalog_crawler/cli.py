"""CLI 진입점 — sitemap → product → normalize → JSON export.

ADR-017: --category boots / uniform 두 카테고리 지원. 통계 집계는
normalizer.compute_match_stats 에 위임하여 cli 가 dict 키 형태에 결합되지 않도록 분리.

ADR-021: --mirror-images 활성화 시 normalize 직후 외부 CDN 이미지를 자체 S3 로 미러링하고
JSON 의 officialImageUrl 을 자체 도메인 URL 로 교체. default off 로 로컬 검증 호환성 유지.
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from collections.abc import Callable
from pathlib import Path

from catalog_crawler.exporter import export
from catalog_crawler.normalizer import (
    CatalogItem,
    compute_match_stats,
    load_brands,
    load_clubs,
    load_silos,
    to_boots_item,
    to_uniform_item,
)
from catalog_crawler.s3_mirror import S3Mirror, build_s3_key
from catalog_crawler.sources import (
    Category,
    CrawlerBlockedError,
    ForbiddenPathError,
    SourceClient,
)
from catalog_crawler.sources.crazy11 import Crazy11Client
from catalog_crawler.sources.kream import KreamClient

# ADR-023: 다중 출처 dispatch. CLI --source 옵션이 선택.
SOURCES: dict[str, type[SourceClient]] = {
    "kream": KreamClient,
    "crazy11": Crazy11Client,
}


# Backward-compat module-level wrappers — 기존 monkey-patch 패턴 (`mocker.patch.object(cli, "...")`)
# 호환을 위해 유지. `_run_pipeline` 가 이들을 통해 호출하므로 테스트가 단위별 mock 가능.
# 핵심 흐름은 SourceClient ABC 의 메서드 호출이며, wrapper 는 단순 위임만.

def discover_boots_product_urls(client: SourceClient, limit: int) -> list[str]:
    """SourceClient.discover_product_urls(BOOTS, limit) wrapper."""
    return client.discover_product_urls(Category.BOOTS, limit)


def discover_uniform_product_urls(client: SourceClient, limit: int) -> list[str]:
    """SourceClient.discover_product_urls(UNIFORM, limit) wrapper."""
    return client.discover_product_urls(Category.UNIFORM, limit)


def parse_product_html(html: str, source_url: str | None = None):
    """Kream 의 parse_product_html shim — 기존 cli.parse_product_html 호출 호환."""
    from catalog_crawler.product_parser import parse_product_html as _parse
    return _parse(html, source_url=source_url)

LOGGER = logging.getLogger("catalog_crawler")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="catalog-crawler",
        description="Catalog 크롤러 — GearShow bulk-import 호환 JSON export",
    )
    parser.add_argument(
        "--source",
        choices=sorted(SOURCES.keys()),
        default="kream",
        help="크롤링 출처 (ADR-023) — kream / crazy11. 기본 kream (기존 동작 보존).",
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
    parser.add_argument(
        "--mirror-images",
        action="store_true",
        default=False,
        help="외부 CDN 이미지를 자체 S3 로 미러링한다 (ADR-021). 운영 적재 시 활성화 필수.",
    )
    parser.add_argument(
        "--s3-bucket",
        default=os.environ.get("AWS_S3_BUCKET"),
        help="--mirror-images 사용 시 업로드 대상 S3 bucket. 미지정 시 AWS_S3_BUCKET 환경변수 사용.",
    )
    parser.add_argument(
        "--s3-region",
        default="ap-northeast-2",
        help="S3 region (기본 ap-northeast-2).",
    )
    parser.add_argument(
        "--s3-prefix",
        default="catalog-images",
        help="S3 객체 prefix (기본 catalog-images, ADR-021 §D2).",
    )

    args = parser.parse_args(argv)
    _configure_logging(args.verbose)

    # --mirror-images 활성화 시 bucket 필수 — fail fast (ADR-021 §D6).
    if args.mirror_images and not args.s3_bucket:
        LOGGER.error(
            "--mirror-images 사용 시 --s3-bucket 또는 AWS_S3_BUCKET 환경변수가 필요합니다",
        )
        return 2

    try:
        if args.category == "uniform":
            return _run_uniform(args)
        return _run_boots(args)
    except (CrawlerBlockedError, ForbiddenPathError) as exc:
        # 정책 위반 — 둘 다 운영자 검수 신호 (exit code 2).
        # CrawlerBlockedError: 403/429 차단 응답. ForbiddenPathError: robots.txt 금지 경로.
        LOGGER.error("크롤러 차단/금지 경로 — 운영자 확인 필요: %s", exc)
        return 2


def _run_boots(args: argparse.Namespace) -> int:
    silos = load_silos()
    LOGGER.info("로드한 사일로: %d", len(silos))

    def normalize(raw: dict) -> CatalogItem:
        return to_boots_item(raw, silos)

    return _run_pipeline(
        args,
        category=Category.BOOTS,
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
        category=Category.UNIFORM,
        normalize=normalize,
        unmatched_label="클럽 매칭 실패 — clubs.yaml 보강 가이드:",
        unmatched_predicate=lambda item: not (item.get("uniformSpec") or {}).get("clubName"),
    )


def _run_pipeline(
    args: argparse.Namespace,
    *,
    category: Category,
    normalize: Callable[[dict], CatalogItem],
    unmatched_label: str,
    unmatched_predicate: Callable[[CatalogItem], bool],
) -> int:
    """공통 파이프라인 — discover → fetch → parse → normalize → [mirror] → export.

    출처(--source)별 ``SourceClient`` 가 ``discover_product_urls`` / ``fetch_product`` /
    ``parse_product`` 를 책임진다 (ADR-023). 이하 흐름은 출처 무관.
    """
    # Source 별 직접 분기 — module-level binding 통해 인스턴스 생성하므로
    # 테스트가 `mocker.patch.object(cli, "KreamClient", ...)` 로 mock 가능.
    if args.source == "kream":
        client: SourceClient = KreamClient(rate_limit_per_sec=args.rate_limit)
    elif args.source == "crazy11":
        client = Crazy11Client(rate_limit_per_sec=args.rate_limit)
    else:
        raise ValueError(f"알 수 없는 출처: {args.source}")
    mirror = (
        S3Mirror(bucket=args.s3_bucket, prefix=args.s3_prefix, region=args.s3_region)
        if args.mirror_images
        else None
    )

    LOGGER.info("[%s] [%s] 상품 URL 수집 중...", args.source, category.value)
    # module-level wrapper 통해 호출 — 테스트 monkey-patch 호환 (실제 흐름은 동일).
    if category == Category.BOOTS:
        candidate_urls = discover_boots_product_urls(client, args.limit)
    else:
        candidate_urls = discover_uniform_product_urls(client, args.limit)
    LOGGER.info("[%s] [%s] 후보 URL: %d", args.source, category.value, len(candidate_urls))

    items: list[CatalogItem] = []
    unmatched: list[str] = []
    fetched = 0
    parse_failed = 0

    try:
        for url in candidate_urls:
            if len(items) >= args.limit:
                break
            try:
                # Kream 은 backward-compat .get(url) — 테스트 fake mock 호환.
                # 다른 source 는 SourceClient.fetch_product (인코딩 등 사이트별 처리).
                if (args.source == "kream"):
                    response = client.get(url)
                else:
                    response = client.fetch_product(url)
            except (CrawlerBlockedError, ForbiddenPathError):
                # 정책 위반 — 부분 결과 dump 후 즉시 상위로 전파 (PR-B: code-reviewer Major #3).
                # 차단 응답을 만나도 다음 URL 로 넘어가면 추가 요청으로 차단이 가중됨.
                # 30개 중 25개 처리 후 차단 시점까지 모은 items 가 영구 유실되는 것을 막는다.
                _dump_partial(items, args.output, category.value)
                raise
            except Exception as exc:  # noqa: BLE001 - 일시적 네트워크/파싱 실패만 흡수
                LOGGER.warning("상품 페이지 fetch 실패 — url=%s, error=%s", url, exc)
                parse_failed += 1
                continue
            if response.status_code != 200:
                parse_failed += 1
                continue
            fetched += 1

            # Kream 출처는 backward-compat 호환 위해 module-level shim 호출 (테스트 mock 가능).
            # 다른 출처(crazy11 등) 는 SourceClient.parse_product 직접 호출 — 사이트별 메타 형식.
            if (args.source == "kream"):
                raw = parse_product_html(response.text, source_url=url)
            else:
                raw = client.parse_product(response, source_url=url)
            item = normalize(raw)

            # ADR-021 §D1: normalize 직후 미러링 — JSON export 시점엔 외부 CDN URL 흔적 0.
            # 실패는 fatal — outer try 의 일반 Exception 분기가 partial dump 처리.
            if mirror is not None:
                # Kream: client.get (backward-compat), 다른 source: client.fetch_product.
                fetcher = client.get if (args.source == "kream") else client.fetch_product
                _mirror_item_image(mirror, fetcher, item, category.value)

            items.append(item)

            if unmatched_predicate(item):
                unmatched.append(raw.get("name") or raw.get("name_ko") or url)
    except (CrawlerBlockedError, ForbiddenPathError):
        # 정책 위반은 inner 에서 이미 _dump_partial 1회 호출 후 raise — 중복 dump 회피.
        # (code-reviewer Major #1 + architecture-reviewer Major #1: outer except 중복 호출 차단)
        raise
    except Exception:
        # inner 가 잡지 않는 예상치 못한 에러 (parse/normalize/mirror 등) 만 outer 가 부분 결과 보존
        _dump_partial(items, args.output, category)
        raise

    match_stats = compute_match_stats(items)
    stats = {
        "category": category.value,
        "candidateUrls": len(candidate_urls),
        "fetched": fetched,
        "parseFailed": parse_failed,
        **match_stats._asdict(),
    }

    export(items, args.output, stats=stats)
    LOGGER.info("[%s] export 완료 → %s (items=%d)", category.value, args.output, len(items))
    LOGGER.info(
        "[%s] 매칭률: silo=%d/%d, club=%d/%d, season=%d/%d, kitType=%d/%d, brand=%d/%d, koFullName=%d/%d",
        category.value,
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


def _mirror_item_image(
    mirror: S3Mirror,
    http_get: Callable,
    item: CatalogItem,
    category: str,
) -> None:
    """item 의 officialImageUrl 을 자체 S3 URL 로 교체. ADR-021 §D1.

    - modelCode 없거나 imageUrl 없으면 미러링 skip + imageUrl null 처리 + warning log
      (운영자가 부분 미러링 인지 가능, 식별자 없는 항목엔 자체 URL 부여 불가).
    - 미러링 실패는 fatal — 호출자(_run_pipeline outer try)가 partial dump + raise.
    """
    src_url = item.get("officialImageUrl")
    model_code = item.get("modelCode")
    if not src_url:
        return
    if not model_code:
        LOGGER.warning(
            "model_code 없어 미러링 skip — imageUrl null 처리: %s", src_url,
        )
        item["officialImageUrl"] = None
        return

    key = build_s3_key(category, model_code, src_url)
    new_url = mirror.upload(src_url, key, http_get=http_get)
    item["officialImageUrl"] = new_url


def _dump_partial(items: list[CatalogItem], output_path: Path, category: str) -> None:
    """부분 결과 보존 — fatal 예외 propagate 전 그때까지 모은 items 를 .partial.json 으로 dump.

    items 가 비어있으면 dump 하지 않는다 (빈 파일 노이즈 방지). 운영자는 .partial.json 을
    검수 후 정식 import 또는 재크롤링 결정.

    stats schema 는 정식 export 와 통일 (test-writer M4):
    `{partial: True, category, items, **MatchStats._asdict()}`. 운영자가 정식 import 와
    같은 jq pipeline 을 쓸 수 있다. `partial: True` 키 존재 여부로 두 schema 구분.
    """
    if not items:
        return
    partial_path = output_path.with_suffix(".partial.json")
    match_stats = compute_match_stats(items)
    export(items, partial_path, stats={
        "partial": True,
        "category": category,
        "items": len(items),
        **match_stats._asdict(),
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
