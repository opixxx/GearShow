# EXEC_PLAN: showcase-search-text

- **Type**: feature
- **Status**: in_progress
- **Risk**: Caution (도메인 변경 + DB 스키마 변경 + 새로운 BC-cross port + ADR 결정 필요)
- **Created**: 2026-05-04
- **Branch**: feature/showcase-search-text
- **Worktree**: /Users/opix/gearshow-showcase-search-text
- **Port**: 9000
- **Base**: main 의 PR #74 + PR #75 + PR #76 머지 후 (`e0ce6b5`) — 모든 의존 해결

---

## 1. 목표

**PR-3 (showcase.search_text 합성)** — PR #75 ADR-016 의 catalog 한국어 alias 컬럼 + PR #76 ADR-017 의 crawler 채움 결과를 **Showcase 검색의 단일 source 컬럼** (`showcase.search_text`) 으로 합성. 후속 PR-4 의 `?keyword=` API 가 단순 LIKE 풀스캔으로 동작하는 기반.

핵심 결과물: 사용자가 한국어 키워드 ("머큐리얼", "맨유") 로 검색했을 때 `LIKE '%머큐리얼%'` 한 줄로 80%+ 매칭률을 내는 검색 인프라.

## 2. 범위

### In
- **Showcase 도메인** — `searchText: String` 필드 추가 (nullable VARCHAR(1000))
- **search_text 합성 정책** (ADR-018):
  - catalog 있을 때 (catalogItemId != null): `fullNameKo + fullNameEn + brand + siloNameKo/clubNameKo + title + description`
  - catalog 없을 때 (직접 입력): `brand + modelCode + title + description`
  - 합성 결과는 공백으로 join, 중복 제거 안 함 (LIKE 매칭은 부분 문자열이라 무해), null/빈 토큰은 제거
- **`SearchTextComposer`** — 합성 헬퍼 (도메인 영역, 순수 함수)
- **`LoadCatalogForSearchPort`** — Showcase application 의 신규 outbound 포트 (catalog BC 한국어 alias read 전용)
- **catalog adapter — `LoadCatalogForSearchAdapter`** — `catalog/adapter/in/...` 또는 `catalog/application` 측에 구현 (BC 격리 유지). port 가 returns `CatalogSearchSource` (catalog 한국어 alias 4개를 묶은 read model VO)
- **`CreateShowcaseFacade`** 흐름에 search_text 합성 통합 (등록 시점 1회)
- **`Showcase.update()`** 시 search_text 재합성 — title/description/modelCode 가 변경되면 영향. catalogItemId 변경은 PR-3 범위 밖 (동일 catalog 가정).
- **JpaEntity / Mapper** 갱신
- **ADR-018** — search_text 합성 정책 (write-side 1회 + 갱신 정책 + 비정규화 트레이드오프)
- **단위/통합 테스트** — catalog 있는 케이스 / 없는 케이스 / update 후 재합성 / 한국어 LIKE 매칭 회귀

### Out
- **PR-4 (`?keyword=` 검색 API)** — 별도 후속, search_text 컬럼 LIKE 쿼리만 추가
- **FULLTEXT 인덱스** — 행 수 ≥10,000 임계 도달 후 ADR-019 결정 (PR-3 시점 미도입, ADR-016 §후속 작업과 정합)
- **catalog 측 alias 갱신 시 Showcase backfill** — admin 이 catalog 한국어 정정 (PR #75 ADR-016 §B3) 후 기등록 Showcase 의 search_text 재합성. 별도 후속 PR (또는 ADR-018 §후속).
- **PR-B (crawler 안정성)** — 본 PR 과 충돌 영역 없음, 병행 가능
- **Flutter 직접 입력 폼 단순화** — 별도 트랙

## 3. 변경 대상

### 백엔드 — 도메인 (수정/신규)
- `showcase/domain/model/Showcase.java` — `searchText` 필드 + `create()` 시그니처 변경 + `update()` 흐름에서 search_text 재합성 trigger
- `showcase/domain/vo/SearchText.java` — 신규, `String` wrapper VO + `compose(...)` 정적 팩토리

### 백엔드 — Application (수정/신규)
- `showcase/application/port/out/LoadCatalogForSearchPort.java` — 신규 포트, `findCatalogSearchSource(Long catalogItemId): Optional<CatalogSearchSource>`
- `showcase/application/dto/CatalogSearchSource.java` — 신규 read model VO: `(fullNameKo, fullNameEn, brand, siloNameKo, clubNameKo)` record
- `showcase/application/service/CreateShowcaseFacade.java` (또는 별도 helper) — search_text 합성 통합
- `showcase/application/service/UpdateShowcaseService.java` — `update()` 후 search_text 재합성
- `showcase/application/service/SearchTextComposer.java` — 합성 헬퍼

### 백엔드 — Adapter (수정/신규)
- `showcase/adapter/out/persistence/ShowcaseJpaEntity.java` — `search_text` 컬럼 추가 (VARCHAR(1000) nullable)
- `showcase/adapter/out/persistence/ShowcaseMapper.java` — searchText 매핑
- `catalog/adapter/out/...` 또는 신규 `catalog/application/service/LoadCatalogForSearchService.java` — `LoadCatalogForSearchPort` 구현. catalog 의 `CatalogItem` + `BootsSpec` + `UniformSpec` 을 read 하여 `CatalogSearchSource` 로 매핑.

### 백엔드 — 테스트 (수정/신규)
- `showcase/domain/model/ShowcaseTest.java` — searchText 필드 + create/update 흐름
- `showcase/application/service/SearchTextComposerTest.java` — 신규, catalog 있음/없음/null 토큰/한국어 정상 매칭
- `showcase/application/service/CreateShowcaseFacadeTest.java` — search_text 합성 검증
- 통합 테스트 — JpaRepository 직접 검증 (PR #75 패턴 재사용), LIKE 한국어 매칭 회귀

### 신규 ADR
- `docs/architecture/adr/ADR-018-showcase-search-text-composition.md`

### 문서
- `docs/diagram/schema.md` — SHOWCASE 테이블에 `search_text` 추가

## 4. 접근

### search_text 합성 규칙

```java
public final class SearchTextComposer {
    private static final int MAX_LENGTH = 1000;

    public static String compose(Showcase showcase, CatalogSearchSource source) {
        List<String> tokens = new ArrayList<>();
        if (source != null) {
            // catalog 있음: 한국어 풀네임 + 영문 풀네임 + brand + spec 한국어 alias
            tokens.add(source.fullNameKo());
            tokens.add(source.fullNameEn());
            tokens.add(source.brand());
            tokens.add(source.siloNameKo());
            tokens.add(source.clubNameKo());
        }
        // catalog 유무 무관: 사용자 직접 입력값
        tokens.add(showcase.getBrand());
        tokens.add(showcase.getModelCode());
        tokens.add(showcase.getTitle());
        tokens.add(showcase.getDescription());

        String joined = tokens.stream()
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining(" "));
        return joined.length() > MAX_LENGTH ? joined.substring(0, MAX_LENGTH) : joined;
    }
}
```

**근거**:
- catalog 있음/없음을 분기하지 않고 **합집합** — 직접 입력값도 항상 포함하여 catalog 의 alias 가 부족한 사일로/클럽도 사용자 description 으로 매칭 가능
- 중복 제거 안 함 — LIKE 부분 문자열 매칭이라 중복은 무해, 정렬 알고리즘 없음
- VARCHAR(1000) 제한 — 한국어 + 영문 풀네임 합치면 평균 100~200자, 1000자면 충분 (catalog 풀네임 255 * 2 + 한국어 alias 등 = 약 600자)
- 1000자 초과 시 truncate — title/description 의 긴 케이스에서 메모리/인덱스 비용 보호

### `LoadCatalogForSearchPort` 시그니처

```java
public interface LoadCatalogForSearchPort {
    Optional<CatalogSearchSource> findCatalogSearchSource(Long catalogItemId);
}

public record CatalogSearchSource(
        String fullNameKo,
        String fullNameEn,
        String brand,
        String siloNameKo,    // BOOTS 의 silo 한국어 alias (UNIFORM 시 null)
        String clubNameKo     // UNIFORM 의 club 한국어 alias (BOOTS 시 null)
) {}
```

**근거**:
- showcase BC 가 catalog domain 직접 import 안 함 — `CatalogSearchSource` record 가 application 레벨 계약
- catalog adapter 가 port 구현, `CatalogItem` + `BootsSpec` + `UniformSpec` 을 read 하여 record 로 매핑
- BOOTS / UNIFORM 분기는 catalog 측 — Showcase 는 한국어 alias 를 그대로 받아 사용

### 갱신 정책 (ADR-018 D3)

| 트리거 | search_text 재합성 |
|---|---|
| Showcase 등록 (`create`) | ✅ 1회 |
| Showcase 수정 (`update` — title/description/modelCode 변경) | ✅ 재합성 |
| Showcase 수정 (catalogItemId 변경) | ⚠️ PR-3 범위 밖 — 동일 catalog 가정 |
| catalog 측 한국어 alias 정정 (PR #75 ADR-016 §B3) | ❌ Showcase 자동 갱신 안 함 — 후속 PR 의 backfill 작업 |
| Showcase 상태 변경 (hide/activate/sold/delete) | ❌ search_text 무관 |

**비용**: catalog alias 정정 직후 Showcase.search_text 가 stale. 후속 PR 의 backfill 으로 해결.

### 운영 마이그레이션

PR #75 ADR-016 D4 와 동일 패턴 — 수동 ALTER + SHOW COLUMNS:

```sql
ALTER TABLE showcase
  ADD COLUMN search_text VARCHAR(1000) NULL,
  ALGORITHM=INSTANT, LOCK=NONE;
SHOW COLUMNS FROM showcase;  -- search_text Null=YES
```

코드 배포 전 ALTER. 배포 후 기등록 Showcase 의 search_text 는 NULL — 별도 backfill 스크립트로 채움 (ADR-018 §후속).

## 5. 단계 (Steps)

### Step 1: domain-search-text-and-vo

**읽어야 할 파일**:
- `showcase/domain/model/Showcase.java`
- `showcase/domain/vo/ShowcaseUpdate.java`

**작업**:
- `Showcase` 도메인에 `searchText` 필드 + `@Builder` 갱신
- `Showcase.create(...)` 시그니처에 `searchText: String` 추가 (nullable)
- `Showcase.update(ShowcaseUpdate)` — title/description/modelCode 변경 시 외부에서 새 search_text 를 받아 적용 (도메인은 합성 책임 X — application 레이어 책임)
- `Showcase.changeSearchText(String)` 신규 메서드 — 외부에서 search_text 를 새로 주입
- 단위 테스트 — `searchText` 보존/null 케이스

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew test --tests "*ShowcaseTest*"
```

**금지사항**:
- 도메인이 `LoadCatalogForSearchPort` 또는 합성 로직을 직접 알면 안 됨 — Aggregate Root 의 책임은 invariant 유지, 합성은 application 책임
- `search_text` 를 도메인 invariant 로 강제 (NOT NULL) 하지 마라 — 갱신 누락 시 등록 자체 차단되면 안 됨

### Step 2: persistence-search-text-column

**읽어야 할 파일**:
- `showcase/adapter/out/persistence/ShowcaseJpaEntity.java`
- `showcase/adapter/out/persistence/ShowcaseMapper.java`

**작업**:
- `ShowcaseJpaEntity` — `@Column(name = "search_text", length = 1000)` 추가, nullable
- `ShowcaseMapper` — toEntity / toDomain 양쪽에서 searchText 매핑
- 운영 ALTER SQL 명세는 ADR-018 D4 에 명시

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew archTest
```

**금지사항**:
- 인덱스 추가 금지 (PR-3 시점) — 검색 API 부재 상태에서 인덱스는 데드 + INSERT 비용

### Step 3: application-port-and-record

**읽어야 할 파일**:
- `catalog/application/port/in/GetCatalogItemUseCase.java`
- `catalog/application/dto/CatalogItemDetailResult.java` (PR #75 머지본의 한국어 alias 노출)

**작업**:
- `showcase/application/port/out/LoadCatalogForSearchPort.java` 신규 — `Optional<CatalogSearchSource> findCatalogSearchSource(Long catalogItemId)`
- `showcase/application/dto/CatalogSearchSource.java` 신규 — record (fullNameKo, fullNameEn, brand, siloNameKo, clubNameKo)
- 단위 테스트 없음 (port 인터페이스 + record 만)

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew archTest   # showcase application 이 catalog domain 직접 import 0
```

**금지사항**:
- `CatalogSearchSource` 에 catalog 도메인 객체 (`CatalogItem`, `BootsSpec`, `UniformSpec`) 직접 노출 금지 — primitive + 한국어 alias 만

### Step 4: catalog-adapter-implementing-port

**읽어야 할 파일**:
- Step 3 산출물
- `catalog/application/service/GetCatalogItemService.java` (또는 비슷)
- `catalog/adapter/out/persistence/CatalogItemPersistenceAdapter.java` (또는 비슷)

**작업**:
- `catalog/application/service/LoadCatalogForSearchService.java` 신규 — `LoadCatalogForSearchPort` 구현
- 또는 `catalog/adapter/out/persistence` 측에 어댑터로 구현 (어디가 더 자연스러운지 ADR-018 D2 에 결정 명시)
- catalog 의 `CatalogItemPort` / `BootsSpecPort` / `UniformSpecPort` 사용해 read → record 매핑
- 단위 테스트

**AC**:
```bash
cd backend
./gradlew test --tests "*LoadCatalogForSearch*"
```

**금지사항**:
- catalog 의 read 시점에 join 쿼리 추가 금지 — 기존 `CatalogItemPort.findById` + `BootsSpecPort.findByCatalogItemId` 두 단순 조회로 충분 (PR #75 패턴)

### Step 5: search-text-composer-and-facade-integration

**읽어야 할 파일**:
- Step 1, 3, 4 산출물
- `showcase/application/service/CreateShowcaseFacade.java`
- `showcase/application/service/UpdateShowcaseService.java` (있으면)

**작업**:
- `showcase/application/service/SearchTextComposer.java` 신규 — `compose(Showcase, CatalogSearchSource): String`
- `CreateShowcaseFacade` 흐름:
  ```java
  CatalogSearchSource source = showcase.getCatalogItemId() != null
      ? loadCatalogForSearchPort.findCatalogSearchSource(showcase.getCatalogItemId()).orElse(null)
      : null;
  String searchText = SearchTextComposer.compose(showcase, source);
  Showcase persisted = showcase.changeSearchText(searchText);
  showcasePort.save(persisted);
  ```
- `UpdateShowcaseService` 흐름 — `Showcase.update()` 후 search_text 재합성. catalogItemId 가 변경되지 않은 경우 동일 source 사용 (별도 조회 회피 가능하나 Step 5 에서는 매번 조회 — 단순화)
- 단위 테스트 — `SearchTextComposerTest`: catalog 있음 / 없음 / null 토큰 / 1000자 초과 truncate / 한국어 부분 매칭 시뮬레이션

**AC**:
```bash
cd backend
./gradlew test --tests "*SearchTextComposer*" --tests "*CreateShowcaseFacade*"
```

**금지사항**:
- `SearchTextComposer` 가 application 의 다른 service / port 에 의존 금지 — 순수 함수
- catalog 조회 결과를 in-memory cache 추가 금지 — 본 PR 범위 밖, 후속 최적화

### Step 6: integration-tests-and-update-flow

**읽어야 할 파일**:
- Step 5 산출물
- `showcase/application/service/CreateShowcaseFacadeIntegrationTest.java` (있으면)
- 또는 `showcase` 통합 테스트 베이스

**작업**:
- 통합 테스트 — Showcase 등록 → JpaRepository 직접 영속 검증 → search_text 합성 결과 검증
- catalog 있는 케이스 (실제 PR #75 의 catalog 등록 후 그 ID 사용)
- catalog 없는 케이스 (catalogItemId=null)
- update 흐름 — title 변경 → search_text 재합성 검증
- 한국어 부분 매칭 회귀 테스트:
  ```java
  // "나이키 머큐리얼" catalog 등록 → Showcase 등록 → search_text LIKE '%머큐리얼%' 매칭 확인
  List<ShowcaseJpaEntity> matches = jdbcTemplate.query(
      "SELECT * FROM showcase WHERE search_text LIKE ?",
      ..., "%머큐리얼%");
  assertThat(matches).hasSize(1);
  ```
- Cucumber 회귀 — 기존 시나리오 통과

**AC**:
```bash
cd backend
./gradlew test
```

### Step 7: adr-018-and-erd

**작업**:
- `docs/architecture/adr/ADR-018-showcase-search-text-composition.md` 신규
  - **Context**: ADR-016 catalog 한국어 alias + ADR-017 crawler 채움 위에 검색 source 통합 필요
  - **D1**: search_text 위치 — Showcase 도메인 컬럼 (별도 테이블 분리 X)
  - **D2**: 합성 책임 — application 레이어 (`SearchTextComposer`), 도메인 X, port X
  - **D3**: 합성 정책 — catalog 합집합 + 직접 입력값 항상 포함, 중복 제거 X, 1000자 truncate
  - **D4**: 갱신 정책 — Showcase create/update 시점 1회, catalog alias 정정 시 backfill 별도 PR
  - **D5**: 운영 마이그레이션 — 수동 ALTER + 코드 배포 + (별도 스크립트) 기존 데이터 backfill
  - **§대안 검토** + **§비용/리스크** + **§롤백** + **§후속 작업** 표준 섹션
- `docs/diagram/schema.md` — SHOWCASE 테이블 표 + Mermaid 에 `search_text VARCHAR(1000) NULL` 추가

**AC**:
```bash
test -f docs/architecture/adr/ADR-018-showcase-search-text-composition.md
grep -q "search_text" docs/diagram/schema.md
```

## 6. 테스트 계획

- **단위 (도메인)**: Showcase searchText 필드 보존, changeSearchText 메서드, update 후 도메인 invariant 유지
- **단위 (composer)**: catalog 있음/없음, null 토큰 제거, 1000자 truncate, 한국어/영문 합집합
- **단위 (port)**: LoadCatalogForSearchService — catalog 없음 → empty Optional
- **통합**: Showcase 등록 → JpaRepository 직접 영속 검증 → search_text 합성 검증, update 후 재합성, catalog 있/없 두 케이스, **한국어 부분 매칭 SQL LIKE 회귀**
- **회귀**: 기존 Showcase 등록/수정 Cucumber 시나리오 통과 (search_text 추가가 기존 흐름 깨지 않음)
- **추가 검증**: ArchUnit (showcase application 이 catalog domain 직접 import 0)

## 7. 완료 기준

```bash
cd backend
./gradlew build   # archTest + test + jacoco + check
test -f docs/architecture/adr/ADR-018-showcase-search-text-composition.md
grep -q "search_text" docs/diagram/schema.md
```

추가:
- [ ] code-reviewer Critical 0 + Major 0
- [ ] architecture-reviewer Critical 0 (BC 격리 + 포트/어댑터 정합)
- [ ] database-optimizer Critical 0 (인덱스 정책 + 마이그레이션 안전성)
- [ ] test-writer 의 누락 케이스 (한국어 부분 매칭 + update 흐름) 본 PR 에서 신규 해결
- [ ] CodeRabbit 통과
- [ ] EXEC_PLAN Status `completed`

## 8. 롤백 전략

- catalog 데이터에 한국어 alias 가 일부 채워진 상태에서 코드 revert — Showcase.search_text 컬럼은 운영 DB 에 남아도 무해 (LIKE 검색이 사용되지 않으면 데드 컬럼)
- 컬럼 자체 제거: `ALTER TABLE showcase DROP COLUMN search_text` (코드 revert 후)
- search_text 데이터 청소: `UPDATE showcase SET search_text = NULL`

## 9. 의존 / 가정

- main 에 PR #74 + PR #75 + PR #76 머지 완료 (확인됨 — base commit `e0ce6b5`)
- catalog 의 신규 한국어 컬럼 4개 + StudType MG/HG + kit_type nullable 화는 ADR-016 §운영 적용 체크리스트의 ALTER 가 운영 DB 에 적용되어 있다고 가정 (사용자 측 운영 작업)
- crawler smoke 는 PR-3 와 무관 — search_text 합성은 catalog 데이터 (DB) 에서 read, crawler 출력 형식과 직접 결합 없음
