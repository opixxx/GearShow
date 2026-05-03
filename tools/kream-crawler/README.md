# Kream Crawler

GearShow `POST /api/admin/catalog/bulk-import` 호환 JSON 을 생성하는 Python 크롤러.

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

```bash
python -m kream_crawler --category boots --limit 30 --output output/2026-05-03.json
```

stdout 예시:

```
[INFO] 로드한 사일로: 30
[INFO] sitemap 으로 상품 URL 수집 중...
[INFO] 후보 URL: 145
[INFO] export 완료 → output/2026-05-03.json (items=30)
[INFO] 매칭률: silo 24/30, studType 28/30
[WARN] 사일로 매칭 실패 — silos.yaml 보강 가이드:
  - Asics Lethal Tigreor 9 IT FG
  - ...
```

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

## 사일로 사전 보강

`kream_crawler/dictionaries/silos.yaml` 에 30개로 시작. 매칭 실패한 상품명이 stdout 으로 출력되면:

```yaml
- canonical: "<canonical 사일로명>"
  brand: "<브랜드>"
  aliases: ["<영문 alias>", "<한글 alias>", ...]
```

형식으로 추가한 뒤 재크롤링.

## 후속 PR 백로그

- 유니폼 카테고리 (clubs.yaml + 시즌/킷타입 추출)
- 사일로 사전 100+ 확장
- Playwright fallback (anti-bot 강화 시)
- 자체 S3 이미지 미러링 (Kream CDN referer 차단 시)
- 재크롤링 스케줄러 (cron + diff import)
