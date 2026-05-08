# ADR-021: Catalog 이미지 수집·저장 정책 — 자체 S3 미러링 + opt-in 플래그 + 도구 rename

- **Status**: Accepted
- **Date**: 2026-05-08
- **Deciders**: opix
- **Related**: ADR-016 (catalog search foundation), ADR-017 (crawler 한국어 매칭 정책), PR #74 (kream crawler)

## Context

PR #74 의 catalog crawler 가 `<meta property="og:image">` 와 JSON-LD `Product.image` 에서 외부 e-커머스 CDN 의 이미지 URL 을 추출하여 `BulkImportCatalogItemRequest.officialImageUrl` 로 그대로 export 한다. 이 결정은 PR #74 시점에 catalog 운영 데이터가 0 행이고 referer 차단 등 실측 결과가 없는 상태에서 보수적 단순화로 채택됐다.

다음 사실이 본 ADR 채택의 트리거:

1. **Referer 차단 실측 (2026-05-08)**: 외부 CDN(`kream-phinf.pstatic.net`) 에 임의 referer (`gearshow.com`, `localhost`, `example.com`) 로 hotlink 시 모두 **HTTP 403 차단**. referer 없으면 200 OK. → 외부 도메인 URL 을 catalog 에 영구 저장하면 GearShow 웹/SSR/SNS 미리보기 등에서 이미지 표시 불가.

2. **운영 데이터 출처 흔적 0 제약 (사용자 정책)**: 운영 catalog DB 와 도구 식별자 모두에서 외부 e-커머스 brand(`kream-phinf`, `pstatic`, `kream-crawler` 등) 흔적을 제거. 약관·법적 안전성 + 운영 독립성.

3. **콘텐츠 출처 검증 결과**: 4 brand(Mizuno/Adidas/New Balance/Nike) 의 modelCode cross-check 에서 외부 e-커머스 이미지 = 제조사 공식 PR 자료. 워터마크 0건. 1120×1120 표준화 흔적은 e-커머스 자체 후처리로 보이나 콘텐츠 출처 자체는 제조사. → 미러링 안전성 baseline 확보.

4. **ADR-017 §후속 PR 백로그**: "자체 S3 이미지 미러링 (외부 CDN referer 차단 시)" 가 reactive 백로그로 명시되어 있었음. 본 ADR 이 그 트리거 충족.

본 ADR 은 다음 결정을 다룬다:

- 미러링 시점·책임 위치
- S3 key 형식 + bucket 정책
- opt-in 플래그 vs 강제 (로컬/운영 분기)
- 출처 정책 (어떤 콘텐츠를 미러링 허용)
- Takedown 절차
- AWS credential 정책
- 도구 식별자 rename (kream-crawler → catalog-crawler)

## Decision

### D1. 미러링 시점 — 크롤러 export 단계 (운영자 도구 책임)

```
discover → fetch HTML → parse → normalize → [if --mirror-images] download+upload → export JSON
                                                       ↑
                                              여기서 imageUrl 교체
```

크롤러 안의 `_run_pipeline` 의 `normalize` 직후에 미러링을 수행하고, JSON export 시점에는 `officialImageUrl` 이 이미 자체 도메인 URL.

**대안 검토**:
- (B) **backend bulk-import 시점**: bulk-import API 가 외부 URL 받으면 자동 다운로드+S3 업로드. backend catalog 도메인 책임이 비대화 (도메인은 자기 사실 정보를 영속·노출하는 역할이지, 외부 콘텐츠를 fetch 해 오는 역할 아님). bulk-import 한 번에 30~1000건 외부 fetch 가 발생해 timeout/rate limit 부담. ❌
- (C) **별도 batch job**: 크롤러는 외부 URL 그대로 export, backend 는 외부 URL 그대로 저장, 별도 mirroring job 이 외부 URL → S3 → DB UPDATE. eventual consistency 단점, 단계 추가, 현 시점 단순성 우선. ❌
- (D) **제조사 사이트에서 직접 수집**: Nike/Adidas/Puma 등 각 제조사 자동화 어려움 (사이트별 구조 다름, 단종 모델 없음). 별도 트랙. ❌

### D2. S3 key 형식 — `catalog-images/<category-lower>/<modelCode>.<ext>`

```
s3://<bucket>/catalog-images/boots/P1GA246550.png
s3://<bucket>/catalog-images/uniform/HV4889-100.png
```

- **prefix**: `catalog-images/` — 기존 GearShow S3 bucket(3D 모델용) 재사용 시 namespace 격리.
- **category subprefix**: `boots/`, `uniform/` — 카테고리별 분리, 향후 카테고리 추가 시 자연 확장.
- **파일명**: `<modelCode>` — 자연 PK 기반. 같은 modelCode 재크롤·재미러링 시 같은 key (멱등).
- **확장자**: src URL path 의 suffix(`.png` / `.jpg` / `.jpeg` / `.webp` / `.gif`). 추론 실패 시 `.bin` fallback.
- **확장자 정규화**: `.jpeg` 는 `.jpg` 로 정규화하여 동일 콘텐츠가 다른 key 로 분기되지 않도록 보장 (멱등성 함정 차단). `s3_mirror.build_s3_key()` 가 강제.

**대안 검토**:
- (B) UUID 기반 key: 멱등성 보장 안 됨 (같은 modelCode 두 번 미러링 시 다른 객체 2개). storage 낭비. ❌
- (C) hash 기반 (`sha256(src_url)`): 멱등이지만 modelCode 와 디커플 — DB 의 official_image_url 만으로 객체 식별 어려움. ❌

### D3. opt-in 플래그 — `--mirror-images` default off

```bash
# 로컬 검증 (Kream URL 그대로, 검색 사슬 동작 확인용)
python -m catalog_crawler --category boots --limit 30 --output output/boots.json

# 운영 적재 (S3 미러링 강제)
python -m catalog_crawler --category boots --limit 30 --output output/boots.json \
  --mirror-images --s3-bucket "$AWS_S3_BUCKET"
```

**근거**:
- 로컬 환경엔 LocalStack 등 S3 emulator 가 없어 미러링 강제 시 셋업 부담 ↑.
- 로컬 검증 목적은 "검색 사슬 동작 확인" 이라 이미지가 referer 차단으로 안 보여도 가능.
- 운영 적재는 운영자가 의도적 opt-in 으로 활성화 → 실수로 외부 URL 적재 차단.

**대안 검토**:
- (B) 모든 환경에서 강제: LocalStack docker-compose 추가 필요. 셋업 비용 + 로컬 변경 폭 증가. 후속 PR 가능. ❌
- (C) profile 기반 자동 분기: `local` / `prod` profile 으로 자동 on/off. 운영자 명시적 의도 약화 (실수로 dev 에서 prod 모드 실행 위험). ❌

### D4. 출처 정책 — 제조사 공식 PR 자료 한정

- **baseline**: 사용자 4-brand cross-check 결과 (Mizuno P1GA246550 / Adidas JS3104 / New Balance ST1FW45 / Nike HV4889-100) — 모두 제조사 공식 사이트의 동일 이미지 확인됨.
- **운영자 의무**:
  - 신규 brand 추가 시 cross-check 의무 (제조사 공식 사이트의 같은 modelCode 이미지가 동일한지).
  - 워터마크 / 자체 스튜디오 흔적 발견 시 즉시 미러링 폐기 + S3 객체 삭제.
- **회색지대 인지**: 1120×1120 표준화 + EXIF 제거 흔적은 외부 e-커머스 자체 후처리로 보이나, **콘텐츠 출처는 제조사 PR 자료**이므로 미러링 안전성은 확보됨. 다만 외부 e-커머스의 변형 저작물 (편집 저작권) 측면은 회색 — D5 takedown 절차로 완화.

### D5. Takedown 절차 — 운영자 수동 절차

외부 출처 측 이의제기 또는 운영자 자체 발견 시:

1. S3 객체 즉시 삭제: `aws s3 rm s3://<bucket>/catalog-images/<category>/<modelCode>.<ext>`
2. catalog 행 backfill: `UPDATE catalog_item SET official_image_url = NULL WHERE model_code = '...'`
3. 사건 로그: `docs/operations/takedown-log.md` 에 일시 + modelCode + 사유 + 처리 결과 기록 (해당 문서 없으면 신규 생성)

**자동화는 별도 PR**. 본 PR 시점은 운영 catalog 행 수 0 이라 자동화 ROI 낮음.

### D6. AWS credential 정책 — 표준 위치만 사용

```
# 운영자 로컬 (운영자가 손으로 적재 실행)
~/.aws/credentials              ← AWS CLI 표준 위치
AWS_PROFILE                     ← 환경변수
AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY  ← 환경변수
```

**금지**:
- 코드에 `aws_access_key_id="AKIA..."` 같은 하드코딩.
- `boto3.client('s3', aws_access_key_id=..., aws_secret_access_key=...)` 인자 명시.
- `pyproject.toml` / `.env` / git tracked 파일에 credential 저장.

**근거**: 보안 + 표준 운영 관행. 운영자는 본인 IAM user 또는 SSO 로 발급받은 credential 을 표준 위치에 저장하고, boto3 default credential resolver 가 자동 발견.

### D7. 도구 식별자 rename — `kream-crawler` → `catalog-crawler`

본 PR 에서 함께 처리:

| 변경 대상 | Before | After |
|----------|--------|-------|
| 디렉토리 | `tools/kream-crawler/` | `tools/catalog-crawler/` |
| Python 패키지 | `kream_crawler/` | `catalog_crawler/` |
| pyproject `name` | `kream-crawler` | `catalog-crawler` |
| CLI 진입점 | `python -m kream_crawler` | `python -m catalog_crawler` |
| 패키지 docstring | "Kream 카탈로그 크롤러" | "Catalog 크롤러" |

**유지 (외부 사이트 식별자 + 사실 정보 docstring)**:
- `KreamClient`, `KREAM_BASE`, `CrawlerBlockedError`, `ForbiddenPathError` — 코드 내부 식별자. 어떤 사이트 fetching 하는지 명시되어야 잘못된 사이트 fetch 차단 가능.
- 모듈 docstring 의 "Kream sitemap 은 카테고리 정보 없이..." 같은 페이지 구조 사실 — 실측 기반 운영 가정.
- README "약관 / 운영 정책" 섹션의 Kream 약관 12조 2항 명시 — 운영자가 어떤 사이트 약관을 따르는지 알아야 함.

다중 출처 추상화 (`sources/<site>.py` 분리, 식별자 일반화) 는 별도 트랙. 본 PR 시점엔 단일 출처라 추상화 비용 > 이득.

### D8. export JSON 메타필드 일관성 — 외부 brand 식별자 사용 금지

`exporter.py` 가 export payload top-level 에 박는 `source` 등 메타필드도 외부 brand 식별자(`"kream"` 등) 를 사용하지 않는다. 일반 토큰 (`"external"`) 으로 통일. 이유:

- 운영 catalog DB 에는 `jq '{items}'` 로 items 만 떼어 전달되므로 메타필드는 운영 데이터에 영향 0. 그러나 운영자 검수용 JSON 파일에는 잔존 → 사용자 정책 "외부 출처 흔적 0" 일관 적용 위해 일반화.
- 출처 식별이 필요한 운영 절차 (takedown — §D5) 는 운영자 수동 절차로 처리. 자동화 시점에 별도 `originSite` 필드 신설 검토.
- 다중 출처 추상화(상기 후속 트랙) 시점에 `originSite` 표준화 함께 진행.

> 후속 발견 (PR #82 PoC 검증): `payload["source"] = "kream"` 잔존 1건 → 본 fix PR 에서 `"external"` 로 일반화.

## 트레이드오프

| 영역 | 비용 | 이득 |
|------|------|------|
| 운영 데이터 정합 | 운영자 의도적 opt-in 부담 | 외부 출처 흔적 0 강제, referer 차단 회피 |
| 콘텐츠 출처 회색지대 | 외부 e-커머스 변형 저작물 측면 잔존 | D4/D5 운영 절차로 완화. 제조사 공식 자료 baseline 으로 안전성 확보 |
| 인프라 추가 | 기존 S3 bucket 재사용으로 신규 인프라 0. boto3 의존성 1개 추가 (~3MB) | 외부 CDN 의존 제거 |
| 로컬 호환성 | `--mirror-images` off 시 외부 URL 잔존 (검증용) | 로컬 셋업 부담 0, 검색 사슬 검증 즉시 가능 |
| Rename 비용 | git mv + import 경로 일괄 치환 | 도구 식별자에서 외부 brand 흔적 제거 |

## 후속 작업

- **자동 워터마크 검증**: 미러링 전 OCR/이미지 분석으로 워터마크 자동 감지. 운영 데이터 규모 확대 시.
- **다중 출처 추상화**: `sources/<site>.py` 분리 + `KreamClient` → `SourceClient` 일반화 + plugin 형태로 신규 출처 추가. 신규 출처 등장 시 트리거.
- **Backend 측 official_image_url 도메인 검증**: bulk-import 시 외부 도메인 URL 거부하는 validator. 운영 catalog 행 수 ≥ 100 도달 시 검토.
- **CDN 도메인 분리**: 현재 S3 직접 URL 사용. 트래픽 증가 시 CloudFront 도입 + URL 형식 변경.
- **Takedown 자동화**: 운영자 admin UI 또는 CLI command 로 modelCode 입력 → S3 삭제 + DB UPDATE 원자 실행.
