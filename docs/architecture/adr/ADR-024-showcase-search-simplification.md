# ADR-024: Showcase 검색 단순화 — title/description 직접 검색

- **Status**: Accepted
- **Date**: 2026-05-12
- **Deciders**: opix
- **Related**: ADR-016 (catalog search foundation), ADR-018 (showcase.search_text 합성, **superseded**), ADR-019 (showcase 검색 키워드 정책, **superseded**)

## Context

배포 직전 시점에 카탈로그(catalog) 기능을 사용자 진입점에서 일시적으로 제외하는 결정이 내려졌다.

- Flutter 등록 플로우에서 `/catalog/search` 경로를 차단하고 모든 등록을 직접 입력 모드(`/create/info`, `catalogItem = null`)로 보낸다.
- catalog 백엔드 API/테이블/서비스는 **데이터 보존을 위해 유지**한다. 향후 카탈로그 기능 복귀 시 그대로 재사용.
- 입력 폼에서 `brand` 필드도 함께 제거한다 — 사용자가 직접 입력하는 메타데이터 중 `category` / `title` / `description` 외에는 등록 시 받지 않는다(`Showcase 직접 입력 폼 단순화` 결정과 정합).

이 변경의 부수 효과로 ADR-018 이 정의한 `search_text` 합성 컬럼의 가치가 크게 약해진다:

| 토큰 | ADR-018 §D3 | 카탈로그 차단 후 |
|------|-------------|------------------|
| `source.fullNameKo` / `fullNameEn` / `brand` (catalog 측) | ✅ (catalog 경로) | ❌ (도달 불가) |
| `source.siloNameKo` / `clubNameKo` (BOOTS/UNIFORM) | ✅ (catalog 경로) | ❌ (도달 불가) |
| `showcase.brand` | ✅ | ❌ (폼에서 받지 않음 → null) |
| `showcase.modelCode` | ✅ | ❌ (폼에서 받지 않음 → null) |
| `showcase.title` / `description` | ✅ | ✅ |

즉 합성 결과는 사실상 `title + description` 만 남는다. 그러면 `search_text` 라는 별도 컬럼·합성기·동기화기·BC 간 outbound port 를 유지할 동기가 사라진다 — 같은 정보를 원본 컬럼에서 직접 검색하면 된다.

본 ADR 은 다음을 결정한다:

1. `search_text` 컬럼/합성/동기화 인프라의 폐기 범위
2. 새 검색 대상(title/description) 과 keyset 페이징 호환
3. `Showcase.brand` 컬럼/도메인 invariant 처리
4. catalog 측 dead code(`LoadCatalogForSearchService`) 정리 범위
5. 운영 마이그레이션 — `schema.sql` 한정 (Flyway 미사용, ADR-016 §D4 환경 그대로)
6. 카탈로그 복귀 시 재합성 인프라 부활의 후속 ADR 트리거

## 사전 발견 (조사 결과)

- 운영 DB `showcase` 행 수 = 0 (2026-05-04 메모리 + 2026-05-12 PR 머지 직전 재확인 필요). backfill / 데이터 손실 비용 없음.
- ADR-018 §D5 의 `search_text` backfill SQL 미실행 상태(운영 행 0 이라 불필요했음).
- `search_text` 컬럼은 `VARCHAR(1000) NULL`. 운영 행이 0 이라 DROP COLUMN 안전.
- Flyway/Liquibase 미사용. `schema.sql` + `ddl-auto: update` 가 single source of truth (`schema.sql` 자체 §정책 주석).
- `ddl-auto: update` 는 컬럼 DROP 을 자동 수행하지 않는다. `schema.sql` 의 명시적 멱등 ALTER 가 필요.

## Decision

### D1. `showcase.search_text` 컬럼 + 합성 인프라 폐기

- `showcase.search_text` 컬럼을 DROP 한다.
- application 의 `SearchTextComposer`, `SearchTextSynchronizer`, outbound port `LoadCatalogForSearchPort` 및 그 record (`CatalogSearchSource`) 를 삭제한다.
- `CreateShowcaseService` / `UpdateShowcaseService` 의 호출부도 함께 제거한다.
- domain `Showcase` 에서 `searchText` 필드와 `changeSearchText(...)` 메서드를 제거한다.

**근거**: ADR-018 §D2~D4 가 합성을 application 레이어에 둔 이유(catalog port 의존 격리)는 catalog 합집합이 있을 때 성립했다. catalog 차단으로 합집합 토큰이 사라지면 application 헬퍼·port·동기화기 모두 단순 `title + description` 을 다시 한 컬럼에 복사하는 비용일 뿐이다.

**대안 검토**:
- (B) 컬럼만 유지하고 합성기를 단순화하여 `title + description` 만 저장: 합성·동기화 코드 유지 비용 + write path 의 redundant 컬럼 채우기. 매칭 효과는 (C) 와 동등. ❌
- (C) (채택) 컬럼·합성기·동기화기·port 모두 폐기 + 검색은 원본 컬럼 직접 LIKE.

### D2. 검색 대상 — `title` + `description` OR LIKE

기존 (ADR-019 §D1):
```jpql
SELECT s FROM Showcase s
 WHERE s.status = 'ACTIVE'
   AND s.searchText IS NOT NULL
   AND s.searchText LIKE :kw ESCAPE '\\'
 ORDER BY s.createdAt DESC, s.id DESC
```

변경:
```jpql
SELECT s FROM Showcase s
 WHERE s.status = 'ACTIVE'
   AND (s.title LIKE :kw ESCAPE '\\'
        OR s.description LIKE :kw ESCAPE '\\')
 ORDER BY s.createdAt DESC, s.id DESC
```

- `ESCAPE '\\'` 와 `%`/`_`/`\\` escape (`ShowcasePersistenceAdapter.escapeLike`) 유지.
- collation(`utf8mb4_0900_ai_ci`)의 case-insensitive 동작 유지(ADR-019 §D1).
- keyset tie-break(`createdAt`, `id`) + `size+1` 페이징 유지.
- `keyword` 의 `@Size(min=1, max=100)` Bean Validation(ADR-019 §D3) 유지.

**계산 모델**:
- 같은 행을 두 컬럼에 대해 LIKE 평가 — 비용 증가는 행당 1회 → 2회 평가. `title` 평균 ~50자, `description` 평균 ~300자. `search_text` 평균 200~600자 와 동일 자릿수.
- 실제 i/o 는 컬럼 사이즈 합과 비례 — `description` 이 `text` 이므로 row off-page TEXT lookup 비용은 `search_text` 인라인 VARCHAR(1000) 대비 약간 증가. N < 10,000 가정 하에서 SLO 영향 미미(p99 < 500ms 유지 추정).
- N≥10,000 도달 시 ADR-018/019 가 제시한 FULLTEXT(n-gram) 도입 검토는 그대로 유효 — 인덱스 대상이 두 컬럼이 되거나, 검색 인덱싱 컬럼을 별도 부활하는 결정은 후속 ADR.

**대안 검토**:
- (B) `description` 만 검색: title 매칭(상품명 직접 입력) 누락. ❌
- (C) `CONCAT(title, ' ', description) LIKE ?`: SQL 함수 호출 + 인덱스 불가. ❌
- (D) MySQL FULLTEXT 즉시 도입: 운영 행 0 시점에 과대 비용. N≥10,000 도달 알람 시 후속 결정. ❌

### D3. `Showcase.brand` — invariant 완화 + 컬럼 보존

- 도메인 `Showcase.create(...)` 의 `brand` 필수 검증을 제거. `brand` 는 null/blank 통과.
- `ShowcaseJpaEntity.brand` 의 `nullable=true` 로 변경.
- 요청 DTO `@NotBlank` 제거, `@Size(max=...)` 만 유지.
- `showcase.brand` 컬럼 자체는 **DROP 하지 않는다**. 카탈로그 복귀 시 catalog 합성에 다시 활용할 여지를 위해 nullable 로만 완화.

**근거**:
- Flutter 입력 폼이 brand 를 받지 않으므로 신규 등록은 항상 `brand=null`.
- 컬럼을 DROP 하지 않는 이유는 향후 카탈로그 복귀 + ADR-018 정책의 부분 부활을 더 저렴하게 만들기 위함(데이터 모델 단절 회피).

**대안 검토**:
- (B) brand 컬럼 DROP: 데이터 모델 단절 + 복귀 비용 증가. ❌
- (C) brand 필드 보존 + invariant 그대로(필수): 폼이 받지 않으므로 백엔드가 빈 문자열 강제 → 의미 없는 양해. ❌

### D4. catalog 측 dead code 정리

- catalog application 의 `LoadCatalogForSearchService` 와 그 어댑터 메서드(있는 경우)를 삭제한다.
- catalog 의 **외부 API**(`/api/catalog/...`), domain (`CatalogItem`, `BootsSpec`, `UniformSpec`), 테이블 (`catalog_item` 등), bulk-import, 크롤러는 **전부 보존**한다.
- showcase BC 의 outbound port `LoadCatalogForSearchPort` 가 사라지므로 호출자 0 → 자연스러운 dead code 정리.

**근거**: 외부 catalog API 자체는 admin 도구 / 카탈로그 재진입 시 살아있다. `LoadCatalogForSearchService` 는 오직 showcase 의 합성 인프라를 위해 존재했던 내부 어댑터이므로 운명을 같이한다.

### D5. 운영 마이그레이션 — `schema.sql` 멱등 ALTER

Flyway 미사용 환경(ADR-016 §D4 의 안티패턴 그대로). `schema.sql` 에 멱등 ALTER 두 블록 추가:

```sql
-- ADR-024 §D1: showcase.search_text 컬럼 제거.
-- ddl-auto: update 는 컬럼 DROP 을 자동 수행하지 않으므로 명시적 ALTER.
-- 운영 행 수 = 0 가정 (PR 머지 직전 SELECT COUNT(*) FROM showcase 로 검증).
-- 멱등성: information_schema 체크 후 동적 SQL 로 DROP.
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'showcase'
     AND column_name = 'search_text'
);
SET @stmt := IF(@col_exists > 0,
                'ALTER TABLE showcase DROP COLUMN search_text',
                'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- ADR-024 §D3: showcase.brand NULL 허용.
-- MODIFY 는 항상 안전 재실행 가능 (idempotent).
ALTER TABLE showcase MODIFY COLUMN brand VARCHAR(255) NULL;
```

운영 적용 순서:
1. **(PR 머지 직전)** `SELECT COUNT(*) FROM showcase` = 0 확인. 0 이 아니면 머지 보류 + backfill 또는 수동 검증.
2. 코드 배포 (= boot 시 `schema.sql` 자동 실행).
3. smoke:
   - `SHOW COLUMNS FROM showcase;` 에 `search_text` 부재, `brand` `Null=YES`.
   - 신규 Showcase 등록 → DB INSERT 성공, brand=NULL 허용 확인.
   - `GET /api/v1/showcases?keyword=...` 가 title/description 매칭으로 정상 응답.

### D6. 카탈로그 복귀 시 후속 ADR

카탈로그 기능이 복귀할 때(시점 미정) 다음 결정을 위한 새 ADR 발행:

- `search_text` 합성 인프라 부활 여부 — 부활하면 ADR-018 정신을 재적용(이름은 새 번호).
- 신규/기존 Showcase backfill 절차 — 행 수에 따라 SQL backfill vs admin endpoint.
- catalog `LoadCatalogForSearchService` / showcase `LoadCatalogForSearchPort` 의 재도입 시그니처.

본 ADR-024 는 폐기 결정에 한정한다. 부활은 별도 결정.

## Consequences

### 긍정

- write path 단순화: `CreateShowcaseService` / `UpdateShowcaseService` 의 합성 호출 사라짐. BC 간 outbound port 1개 감소.
- 검색 결과 동작이 사용자 멘탈 모델과 직관적으로 일치 — "내가 적은 제목과 설명에서 찾는다".
- 데이터 모델이 평탄해짐 — `showcase` 테이블에 redundant 컬럼 1개 제거.
- 카탈로그 복귀 시 brand 컬럼이 nullable 로 살아있어 점진적 부활(catalogItemId 기반 brand 복원 등) 가능.

### 비용 / 리스크

- `description` 이 `text` 컬럼이라 off-page lookup 비용이 인라인 VARCHAR(1000) 보다 미세하게 큼 — N < 10,000 에서는 영향 미미.
- `Showcase.brand` 가 nullable 이 되어 검색 토큰에서 의미 손실(brand 자체가 폼에서 빠지므로 사실상 무손실).
- `Showcase.modelCode` 도 검색 토큰에서 제외 — 폼에서 받지 않는 한 영향 0. 향후 modelCode 입력을 다시 받게 되면 description 에 자연 포함되도록 UX 안내 또는 별도 ADR.
- ADR-018/019 의 운영 절차 문서(backfill SQL, FULLTEXT 임계 모니터링) 일부가 ADR-024 와 정합 재검토 필요 — supersede 표시로 출처 명시.

### 후속 작업 (별도 PR)

- prod 환경의 `ddl-auto: update` 안티패턴 해소 (ADR-016 §D4 / ADR-018 §D5 가 명시) — 본 ADR 범위 밖.
- 카탈로그 복귀 ADR (시점 미정).
- 검색 분석(인기 키워드, 매칭 실패율) — 카탈로그 복귀와 무관하게 운영 후 후속 ADR-020 트리거.
- **인덱스 보강 (database-optimizer 권고)**: `ShowcaseJpaEntity.@Table.indexes` 에 `(showcase_status, created_at DESC, id DESC)` + `(owner_id, showcase_status, created_at DESC, id DESC)` 명시 추가. Repository 의 keyset/keyword 쿼리 모두 `status='ACTIVE'` + ORDER BY 이므로 풀스캔+filesort 회피 효과. 운영 행 0 시점 안전. ADR-018/019 시점부터 누락된 부채로 본 ADR 변경과 별개 PR.
- **Flutter sentinel 정리 (code-reviewer 권고)**: `ShowcaseDraft.catalogItem` 을 `CatalogItemSummary?` 로 nullable 변경 + 직접 입력 모드의 임시 `CatalogItemSummary(catalogItemId=0, brand='', modelCode='')` sentinel 제거. 카탈로그 복귀 시 nullable→nonnull 분기만 살리면 됨.
- **ShowcaseJpaEntity contentHash Javadoc 정리**: P1-B TODO 단락 (이미 해소된 작업) 제거 — 별도 chore PR.

### 운영 적용 체크리스트

- [ ] **(PR 머지 직전)** `SELECT COUNT(*) FROM showcase` = 0 확인 (0 아니면 머지 보류)
- [ ] 코드 배포 → boot 시 `schema.sql` 자동 적용
- [ ] **(배포 직후)** `SHOW COLUMNS FROM showcase` 로 `search_text` 부재 + `brand` `Null=YES` 확인
- [ ] **(배포 직후)** 신규 Showcase 등록 (brand 미전송) → 200 + DB 저장 확인
- [ ] **(배포 직후)** `GET /api/v1/showcases?keyword=...` 매칭 smoke

### 롤백

운영 행 0 가정에서의 단순 revert:

1. 코드 revert (PR revert).
2. `schema.sql` 의 ALTER 블록 자동 제거 → boot 시 컬럼 부재. 다음 부팅이 `ddl-auto: update` 로 entity 의 `searchText` 필드(혹은 revert 후 부활하는 도메인 필드) 에 맞춰 컬럼을 재추가하지만, 운영 행 0 이라 데이터 손실 없음.
3. ADR-018 §D5 의 backfill SQL 을 재실행하여 합성 컬럼 채움(필요 시).
4. ADR-018/019 의 Status 를 `Accepted` 로 환원, ADR-024 는 `Deprecated (rolled back YYYY-MM-DD)` 로 표기.

운영 행이 0 이 아닌 시점의 롤백은 본 ADR 범위를 벗어남 — 별도 데이터 복구 ADR 필요.
