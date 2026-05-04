# Kream Crawler

GearShow `POST /api/admin/catalog/bulk-import` 호환 JSON 을 생성하는 Python 크롤러. **boots / uniform** 두 카테고리 지원 (ADR-017).

## 약관 / 운영 정책 (반드시 읽을 것)

- **수집 대상**: Kream 의 축구화 카탈로그(공개 sitemap 노출 상품). robots.txt(`Allow: /`, `Sitemap: https://kream.co.kr/sitemap.xml`) 와 약관 12조 2항(서비스로 얻은 정보의 영리목적 이용/제3자 이용 금지)을 인지하고 다음 정책으로 운영한다.
- **운영자 수동 검수 강제**: 본 크롤러는 JSON export 까지만 수행하고, GearShow 백엔드로 자동 import 하지 않는다. 운영자가 export 된 JSON 을 직접 검수·재가공한 뒤 `/api/admin/catalog/bulk-import` 에 명시적으로 POST 한다.
- **raw 데이터 영구 저장 금지**: GearShow DB 에는 정규화된 사실 정보(브랜드/모델코드/사일로/이미지 URL)만 저장한다. Kream 의 시세·가격·거래 이력은 저장하지 않는다.
- **Rate limit 1 req/sec**: `KreamClient` 가 강제. 업무 방해 회피.
- **차단 경로**: `/my/**`, `/history/**` 는 client 단에서 거부 (robots.txt 명시 차단).
- **User-Agent 명시**: `GearShow-Catalog-Bot/1.0 (+contact: opix0306@naver.com)` 로 운영자가 차단/문의 가능하게 한다.
- **Anti-bot 우회 시도 금지**: 403/429 응답 시 즉시 중단 (`CrawlerBlockedError`).

## 셋업

```bash
cd tools/kream-crawler
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
pytest
```

## 사용법

### 1. admin 토큰 발급 (PR #72 의 admin BC)

```bash
curl -X POST https://api.gearshow.com/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@gearshow.com","password":"<영구 비번>"}' \
  | jq -r .data.accessToken \
  > /tmp/admin_token

export GEARSHOW_ADMIN_TOKEN="$(cat /tmp/admin_token)"
```

### 2. 크롤링 → JSON export

축구화:
```bash
python -m kream_crawler --category boots --limit 30 --output output/2026-05-04-boots.json
```

유니폼 (ADR-017):
```bash
python -m kream_crawler --category uniform --limit 30 --output output/2026-05-04-uniform.json
```

stdout 예시 (boots):

```
[INFO] 로드한 사일로: 30
[INFO] [BOOTS] 검색 endpoint 으로 상품 URL 수집 중...
[INFO] [BOOTS] 후보 URL: 145
[INFO] [BOOTS] export 완료 → output/2026-05-04-boots.json (items=30)
[INFO] [BOOTS] 매칭률: silo=24/30, club=0/30, season=0/30, kitType=0/30, brand=29/30, koFullName=29/30
[WARN] 사일로 매칭 실패 — silos.yaml 보강 가이드:
  - Asics Lethal Tigreor 9 IT FG
  - ...
```

stdout 예시 (uniform):

```
[INFO] 로드한 사전: clubs=32, brands=8
[INFO] [UNIFORM] 검색 endpoint 으로 상품 URL 수집 중...
[INFO] [UNIFORM] export 완료 → output/2026-05-04-uniform.json (items=15)
[INFO] [UNIFORM] 매칭률: silo=0/15, club=12/15, season=14/15, kitType=11/15, brand=15/15, koFullName=14/15
[WARN] 클럽 매칭 실패 — clubs.yaml 보강 가이드:
  - Some Lower-League Club Jersey 24/25
  - ...
```

### 매칭률 기준 (ADR-017 §D4)

| 지표 | 하한 | 미달 시 |
|---|---|---|
| `silo_matched / total` (BOOTS) | ≥ 70% | `silos.yaml` 보강 |
| `club_matched / total` (UNIFORM) | ≥ 70% | `clubs.yaml` 보강 |
| `season_extracted / total` (UNIFORM) | ≥ 80% | 시즌 정규식 보강 |
| `brand_matched / total` | ≥ 95% | `brands.yaml` 보강 |
| `korean_full_name_present / total` | ≥ 95% | Kream keywords 형식 변경 의심 — `parse_keywords()` 재검토 |

`kit_type_inferred` 는 빈티지(정상 None)와 추출 실패 구분 불가라 명시 하한선 없음.

### 3. 운영자 검수

```bash
# JSON 열어서 silo 매칭 실패 항목, modelCode 누락, image_url 깨진 케이스 등 수정
$EDITOR output/2026-05-03.json
```

### 4. bulk-import 호출

```bash
# items 배열만 떼어 백엔드 BulkImportCatalogItemRequest 형식으로 POST
jq '{items}' output/2026-05-03.json | curl -X POST \
  https://api.gearshow.com/api/admin/catalog/bulk-import \
  -H "Authorization: Bearer $GEARSHOW_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d @-
```

응답:

```json
{
  "status": 200,
  "code": null,
  "message": "카탈로그 일괄 등록 처리 완료",
  "data": {
    "created": 24,
    "skippedDuplicate": 4,
    "failed": 2,
    "errors": [...]
  }
}
```

## 사전 보강 가이드 (ADR-017 §D2)

세 yaml 사전 모두 `kream_crawler/dictionaries/` 에 위치. 매칭 실패 상품명이 stdout 에 출력되면 직접 편집 + 재크롤링.

### `silos.yaml` (축구화 사일로)
```yaml
- canonical: "<canonical 사일로명>"
  brand: "<브랜드>"
  aliases: ["<영문 alias>", "<한글 alias>", ...]
```

### `brands.yaml` (브랜드 + 한국어 alias)
```yaml
- canonical: "Nike"
  aliases: ["나이키"]
```

### `clubs.yaml` (클럽 + 한국어 alias + league)
```yaml
- canonical: "Manchester United"
  aliases: ["맨체스터 유나이티드", "맨유"]
  league: "EPL"
- canonical: "Korea"          # 국가대표
  aliases: ["대한민국", "한국"]
  league: null                # 국가대표는 league null
```

`aliases` 의 첫 한국어 항목이 `siloNameKo` / `clubNameKo` 로 채워지므로 한국어 alias 의 첫 위치는 의도된 표기를 둔다.

## 매칭 알고리즘 (ADR-017 §D3)

- Longest match wins (긴 alias 우선)
- 동률 시 canonical 알파벳 정렬 (결정성 보장)
- `match_brand`: 영문 canonical 우선, 실패 시 한국어 alias fallback
- `match_club`: 영/한 양쪽 검색
- `extract_kit_type`: 영문 우선, 실패 시 한국어 fallback (`홈`/`어웨이`/`원정`/`써드`/`서드`)

## 후속 PR 백로그

- crawler 안정성 (defusedxml / 부분 결과 partial.json / `except CrawlerBlockedError` re-raise) — PR-B
- 사일로/클럽 사전 100+ 확장 (운영 매칭률 측정 후)
- Playwright fallback (anti-bot 강화 시)
- 자체 S3 이미지 미러링 (Kream CDN referer 차단 시)
- 재크롤링 스케줄러 (cron + diff import)

## 참조

- ADR-016: catalog search foundation (한국어 alias 컬럼 + StudType MG/HG + kitType nullable)
- ADR-017: crawler 한국어 매칭 정책 (본 도구의 사전 + 매칭 알고리즘 + 매칭률 기준)
- 백엔드 contract: `BulkImportCatalogItemRequest.Item` (PR #71) + ADR-016 신규 필드 (PR #75)
