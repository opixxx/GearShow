# ADR-018: Showcase 검색 텍스트 합성 정책

- **Status**: Accepted
- **Date**: 2026-05-04
- **Deciders**: opix
- **Related**: ADR-016 (catalog search foundation), ADR-017 (crawler 한국어 매칭 정책), PR #75, PR #76

## Context

ADR-016 이 catalog 도메인에 한국어 alias 컬럼 (`fullNameKo/En`, `siloNameKo`, `clubNameKo`) 을 도입하고 ADR-017 의 crawler 가 이를 채우게 했다. 다음 단계는 **사용자 검색 진입점인 Showcase 가 한국어 키워드 ("머큐리얼", "맨유") 로 매칭 가능하도록** catalog 의 한국어 alias 와 Showcase 자체 입력값 (title, description, brand, modelCode) 을 단일 검색 source 로 합성하는 것이다.

본 ADR 은 다음 결정을 다룬다:

1. 검색 텍스트의 저장 위치 (Showcase 컬럼 vs 별도 테이블)
2. 합성의 책임 위치 (도메인 vs application vs adapter)
3. 합성 정책 (catalog 분기 vs 합집합, 중복 제거, 길이 제한)
4. 갱신 정책 (등록/수정/catalog 정정 시 어떻게)
5. 운영 마이그레이션

**범위 명시**: `?keyword=` 검색 API 자체와 FULLTEXT 인덱스 도입은 본 ADR 범위 밖 — 후속 PR-4 + ADR-019 에서 다룬다.

## 사전 발견 (조사 결과)

- **Showcase.catalogItemId nullable**: 사용자 직접 입력 (catalog 미선택) 케이스 지원. 검색 텍스트는 두 케이스 모두 채워져야 함.
- **catalog API 의 한국어 컬럼 노출**: PR #75 ADR-016 §B2 에서 `CatalogItemDetailResult` 에 `fullNameKo/En` + `siloNameKo` + `clubNameKo` 노출 완료. read 측 인터페이스가 이미 존재.
- **Showcase domain 변경 빈도**: title/description/modelCode 가 사용자 수정 가능 — search_text 도 동기 갱신 필요.
- **catalog alias 정정**: PR #75 ADR-016 §B3 의 `CatalogItem.update(... fullNameKo/En)` 가 admin 정정 경로 — 정정 후 기등록 Showcase 의 search_text 가 stale.

## Decision

### D1. search_text 위치 — Showcase 도메인 컬럼

`showcase` 테이블에 `search_text VARCHAR(1000) NULL` 컬럼 추가. 별도 read view 또는 합성 전용 테이블 분리하지 않는다.

**대안 검토**:
- (B) 별도 `showcase_search_text(showcase_id, search_text)` 테이블: write-side aggregate 와 read-side 검색 텍스트의 책임 분리는 깔끔하나, (1) 모든 검색 쿼리가 join 추가, (2) Showcase 등록 시 두 테이블 동시 INSERT 필요, (3) Showcase 행 수만큼 동일 카디널리티라 분리 이득 없음. ❌
- (C) 머터리얼라이즈드 뷰: MySQL 미지원 (PostgreSQL 만), 직접 구현은 운영 복잡도. ❌
- (D) Elasticsearch 등 외부 검색 엔진: PR-3 시점은 N=10,000 미만으로 추정 — 외부 의존 도입은 과도. ADR-016 §후속 작업의 FULLTEXT(n-gram) 임계 도달 시 재검토. ❌

### D2. 합성 책임 위치 — application 레이어 (`SearchTextComposer`)

도메인 (`Showcase`) 은 search_text 를 단순 보존 (changeSearchText) 만. catalog source 조회 + 합성은 application 의 정적 헬퍼 `SearchTextComposer`.

```java
public final class SearchTextComposer {
    public static String compose(Showcase showcase, CatalogSearchSource source) { ... }
}
```

**근거**:
- 합성은 read 보강이지 invariant 가 아니다 — Aggregate Root 의 책임이 아님.
- 도메인이 catalog port (`LoadCatalogForSearchPort`) 를 의존하면 헥사고날 경계 위반. application 이 port 호출 + 도메인에 결과 주입.
- 합성 로직이 PR-4 검색 결정에 따라 진화할 가능성 — application 레벨 헬퍼가 변경 격리 단위로 적합.

**BC 격리**:
- showcase application 의 `LoadCatalogForSearchPort` (outbound port) + `CatalogSearchSource` (record) — showcase BC 가 catalog 도메인 객체를 직접 import 하지 않는다.
- catalog application 의 `LoadCatalogForSearchService` 가 port 구현. `CatalogItem` + `BootsSpec` + `UniformSpec` 을 read 후 record 로 평탄화.

**대안 검토**:
- (B) 도메인 메서드 (`Showcase.composeSearchText(catalogSource)`): 도메인이 application DTO 를 알아야 함 + 합성 트리거가 도메인에 박힘. ❌
- (C) Showcase JpaEntity 의 `@PrePersist` / `@PreUpdate` hook: catalog 조회를 JPA 라이프사이클 hook 안에서 하면 N+1 + 트랜잭션 경계 깨짐. ❌

### D3. 합성 정책 — 합집합 + 1000자 truncate

| 토큰 | catalog 있음 | catalog 없음 |
|---|---|---|
| `source.fullNameKo` | ✅ | — |
| `source.fullNameEn` | ✅ | — |
| `source.brand` | ✅ | — |
| `source.siloNameKo` (BOOTS) | ✅ | — |
| `source.clubNameKo` (UNIFORM) | ✅ | — |
| `showcase.brand` | ✅ | ✅ |
| `showcase.modelCode` | ✅ | ✅ |
| `showcase.title` | ✅ | ✅ |
| `showcase.description` | ✅ | ✅ |

- 모든 토큰을 공백 (`" "`) 으로 join
- `null` / `blank` 토큰은 제거
- 중복 제거 안 함 (LIKE 부분 문자열은 중복 무해)
- 1000자 초과 시 `substring(0, 1000)` truncate

**근거**:
- catalog 한국어 alias 가 부족한 사일로/클럽 (운영 후 발견) 도 사용자 description 으로 매칭 가능 — 합집합이 매칭률 보강
- VARCHAR(1000): catalog 풀네임 (255*2) + 한국어 alias 100~200 + title (보통 100 미만) + description 평균 300 = 약 600~900자. 1000자 마진 충분.
- Hibernate `@Column(length = 1000)` 의 `String` 타입은 utf8mb4 로 4000 bytes (16384 가능) — MySQL 8 default row size 에 안전.

**대안 검토**:
- (B) 중복 제거 (예: brand 가 source.brand 와 showcase.brand 둘 다 매칭되면 한 번만): set 기반 중복 제거는 LIKE 매칭에 영향 없으나 추가 비용. PR-4 의 검색 정확도에 영향 없음. ❌
- (C) 길이 무제한 (TEXT): FULLTEXT(n-gram) 후속 도입 시 인덱스 키 길이 제약 (DYNAMIC=3072 bytes) 에 걸림. 1000자가 utf8mb4 4000bytes 라 마지노선. ❌
- (D) 검색용 정규화 (예: 소문자 변환, 한글 자모 분리): 후속 ADR-019 에서 PR-4 검색 정책과 함께 결정. 본 ADR 범위 밖.

### D4. 갱신 정책 — Showcase 등록/수정 시점 1회

| 트리거 | search_text 재합성 |
|---|---|
| Showcase 등록 (`CreateShowcaseService.saveShowcaseWithSpec`) | ✅ 1회 |
| Showcase 수정 (`UpdateShowcaseService.update`) | ✅ 매 update 시 재합성 (catalog source 재조회) |
| Showcase 상태 변경 (hide/activate/sold/delete) | ❌ search_text 무관 |
| Showcase content_hash dedup (기존 Showcase 반환) | ❌ 기존 search_text 유지 |
| catalog 측 한국어 alias 정정 (PR #75 ADR-016 §B3) | ❌ Showcase 자동 갱신 안 함 — **별도 backfill 후속 PR** |

**근거**:
- 등록 + 수정 시점 1회 합성으로 일관성 보장. catalogItemId 가 update 흐름에서 변경되지 않으므로 동일 source 재조회 (단순화 — 캐싱은 본 PR 범위 밖).
- catalog alias 정정 시 Showcase 자동 갱신은 BC-cross side effect 가 발생 (UpdateCatalogItemService → ShowcasePort) — 의존 역전 + 트랜잭션 경계 모호 → 후속 PR 의 backfill 스크립트로 분리 (ADR-018 §후속 작업).

**비용**: catalog alias 정정 직후 기등록 Showcase.search_text 가 stale. 운영자가 catalog 정정 후 backfill 명시 트리거 필요.

**대안 검토**:
- (B) catalog Update 시 hook → Showcase 자동 backfill: BC 격리 깨짐. ❌
- (C) Showcase read 시점에 search_text 재합성 (lazy): N=10,000 시 매 read 마다 catalog 조회 → 성능 저하. read 빈도 ≫ write 빈도. ❌

### D5. 운영 마이그레이션

`ddl-auto: update` 는 운영 안티패턴 (PR #75 ADR-016 §D4 참조). 수동 ALTER 후 코드 배포:

```sql
-- 코드 배포 전:
ALTER TABLE showcase
  ADD COLUMN search_text VARCHAR(1000) NULL,
  ALGORITHM=INSTANT, LOCK=NONE;
SHOW COLUMNS FROM showcase;   -- search_text Null=YES 확인

-- 코드 배포 후 (smoke):
INSERT 1 신규 Showcase → SELECT search_text 가 채워졌는지 확인
```

**기존 데이터 backfill** (선택):
```sql
-- 기등록 Showcase 의 search_text 는 NULL 인 채로 남음 — 검색 결과에서 빠짐.
-- 운영자가 backfill 원하면 별도 스크립트 또는 admin endpoint:
UPDATE showcase SET search_text = CONCAT_WS(' ', brand, model_code, title, description)
WHERE search_text IS NULL AND catalog_item_id IS NULL;
-- catalog 연결 Showcase 는 catalog_item join 으로 한국어 alias 까지 합성 (별도 스크립트)
```

backfill 은 본 PR 범위 밖 — 운영 진행 후 결정.

## Consequences

### 긍정

- 후속 PR-4 의 `?keyword=` 검색이 단일 LIKE 쿼리로 동작 (`WHERE search_text LIKE '%keyword%'`). 한국어 + 영문 + 직접 입력 모두 한 컬럼에서 매칭.
- 합성 책임이 application 의 단일 헬퍼에 집중 — 변경 격리 단위 명확.
- BC 격리 유지: showcase BC 가 catalog 도메인 객체를 직접 의존하지 않음.
- catalog 데이터를 변경하지 않고 Showcase 측에만 비정규화 — catalog 의 write 정합성 영향 없음.

### 비용 / 리스크

- Showcase 행 크기 증가 — VARCHAR(1000) 평균 200~600자 사용. 행 수 증가 시 page split 빈도 영향 — 다만 PR-4 의 LIKE 풀스캔 시점에 재검토.
- search_text 가 catalog alias 와 stale 가능 — admin 의 catalog 정정 후 backfill 까지 시간차.
- 합성 로직이 SearchTextComposer 한 곳에 집중되어 있어 변경 시 기존 데이터의 search_text 가 stale — 정책 변경 시 backfill 스크립트 필수.

### 후속 작업 (별도 PR)

- **PR-4**: `?keyword=` 검색 API + Cucumber 인수 + (조건부) FULLTEXT 인덱스. ADR-019 (검색 정규화 정책) 와 함께.
- **ADR-019** (TBD): 검색 정규화 (소문자/자모 분리/공백 처리) 정책. PR-4 시점.
- **Showcase backfill 스크립트 또는 admin endpoint**: catalog alias 정정 후 기등록 Showcase 의 search_text 재합성.
- **FULLTEXT 도입** (ADR-016 §후속 작업의 임계 N≥10,000 도달 시): MySQL `WITH PARSER ngram` + `ngram_token_size=2`. 본 컬럼에 `FULLTEXT INDEX` 추가.

### 운영 적용 체크리스트

- [ ] `ALTER TABLE showcase ADD COLUMN search_text VARCHAR(1000) NULL, ALGORITHM=INSTANT, LOCK=NONE;`
- [ ] `SHOW COLUMNS FROM showcase;` — `search_text` `Null=YES` 확인
- [ ] 코드 배포
- [ ] smoke: 신규 Showcase 등록 → DB 에서 `SELECT search_text` 가 합성 결과로 채워졌는지 확인
- [ ] 기등록 데이터 backfill 결정 (선택) — 운영자 판단

### 롤백

- 코드 revert: search_text 컬럼은 운영 DB 에 남아도 무해 (LIKE 쿼리 미사용)
- 컬럼 자체 제거 (코드 revert 후): `ALTER TABLE showcase DROP COLUMN search_text`
- 데이터 청소: `UPDATE showcase SET search_text = NULL`
