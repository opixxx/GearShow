# EXEC_PLAN: showcase-keyword-search

- **Type**: feature
- **Status**: in_progress
- **Risk**: Caution (검색 본체 — 사용자 첫 검색 시점, ADR-019 결정 필요)
- **Created**: 2026-05-04
- **Branch**: feature/showcase-keyword-search
- **Worktree**: /Users/opix/gearshow-showcase-keyword-search
- **Port**: 9000
- **Base**: main 의 PR #74/#75/#76/#77/#78 머지 후 (`8ed5eef`)

---

## 1. 목표

**PR-4 (`?keyword=` 검색 API)** — ADR-016 (catalog 한국어 alias) + ADR-017 (crawler 채움) + ADR-018 (showcase.search_text 합성) 위에 **사용자에게 처음 보이는 한국어 검색 기능**.

`GET /api/v1/showcases?keyword=머큐리얼` 같은 단일 키워드 LIKE 매칭으로 한국어/영문 풀네임 + brand + spec 한국어 alias + 직접 입력값 모두에서 매칭. ADR-019 로 검색 정규화 정책 (대소문자 / 공백) + FULLTEXT 임계 정립.

## 2. 범위

### In
- **`GET /api/v1/showcases?keyword=`** — 기존 list endpoint 에 `keyword` 옵션 파라미터 추가
- **`ListShowcasesUseCase.list(...)` 시그니처 확장** — `keyword: String | null` 인자
- **`ShowcasePort` 확장** — `findByKeywordFirstPage(keyword, size)` + `findByKeywordWithCursor(keyword, cursor..., size)` 또는 기존 메서드에 keyword 옵셔널 흡수
- **`ShowcaseJpaRepository`** — `findBySearchTextContaining` 또는 `@Query` 로 LIKE 매칭. cursor 페이징 동일 형식
- **검색 정규화 (ADR-019 D1)**: `LOWER(search_text) LIKE LOWER('%keyword%')` — 대소문자 무시. 한글은 case-insensitive 영향 없음. 공백 정규화는 본 PR 범위 밖 (자모 분리 등 후속).
- **`@Size(min=1, max=100)`** keyword Bean Validation
- **단위/통합 테스트** — JpaRepository LIKE 회귀, 한국어/영문/대소문자 매칭, 빈 결과, 누락 search_text (NULL) 행은 결과 제외
- **Cucumber 인수 테스트** — `?keyword=머큐리얼` 시나리오 (catalog 연결 + 직접 입력 둘 다 매칭, 누락 행 제외)
- **ADR-019** — D1 LOWER 정규화 / D2 FULLTEXT 임계 (행 수 N≥10,000) / D3 keyword 길이 제한 / D4 backfill prerequisite 명시 / D5 정규화 미적용 영역 (자모 분리 등) 후속 결정

### Out
- **FULLTEXT(n-gram) 인덱스 도입** — N=10,000 미만 가정으로 본 PR 미적용. ADR-019 §D2 임계 도달 시 별도 PR.
- **자모 분리 / 공백 정규화 등 고급 검색 정규화** — ADR-019 §D5 후속 (PR-5 또는 ADR-020).
- **검색 결과 정렬 — relevance / 매칭 위치 가중** — 본 PR 은 단순 cursor (createdAt DESC). 후속.
- **Search analytics / 인기 키워드** — 후속.
- **기등록 Showcase backfill** — ADR-018 §D5 의 운영자 직접 실행. 본 PR 의 prerequisite 으로만 명시.

## 3. 변경 대상

### 백엔드 (수정)
- `showcase/application/port/in/ListShowcasesUseCase.java` — `list(...)` 에 `keyword: String | null` 파라미터 추가
- `showcase/application/service/ListShowcasesService.java` — keyword null/non-null 분기, port 호출
- `showcase/application/port/out/ShowcasePort.java` — `findByKeywordFirstPage` / `findByKeywordWithCursor` 메서드 추가
- `showcase/adapter/out/persistence/ShowcasePersistenceAdapter.java` — JpaRepository LIKE 위임
- `showcase/adapter/out/persistence/ShowcaseJpaRepository.java` — `@Query` 로 `LOWER(s.searchText) LIKE LOWER(:pattern)` + cursor 조건. ACTIVE 상태만, search_text NULL 제외
- `showcase/adapter/in/web/ShowcaseController.java` — `?keyword=` 옵셔널 파라미터 + `@Size`
- `MyShowcaseController.java` 는 본 PR 범위 밖 (내 쇼케이스 검색은 후속)

### 백엔드 (테스트)
- `showcase/application/service/ShowcaseSearchKeywordIntegrationTest.java` (신규) — JpaRepository LIKE 회귀
  - catalog 연결 한국어 매칭 (`?keyword=머큐리얼`)
  - 직접 입력 한국어 매칭 (`?keyword=어떤 키워드`)
  - 영문 매칭 (`?keyword=Mercurial`)
  - 대소문자 무시 (`?keyword=MERCURIAL`)
  - search_text NULL 행 결과 제외 (backfill 안 한 기등록)
  - cursor 페이징 동작
- `ShowcaseControllerTest` 또는 통합 — 컨트롤러 진입 검증
- Cucumber `showcase.feature` — `?keyword=` 시나리오

### 신규 ADR
- `docs/architecture/adr/ADR-019-showcase-search-keyword-policy.md` — D1 LOWER 정규화 / D2 FULLTEXT 임계 + 부하 모니터링 / D3 keyword 길이 / D4 backfill prerequisite / D5 후속 정규화 정책

### 운영 (체크리스트)
- ADR-018 §D5 의 backfill 1회 실행 — PR-4 머지 직전 prerequisite

## 4. 접근

### Repository 쿼리

```java
// ShowcaseJpaRepository
@Query("""
    SELECT s FROM ShowcaseJpaEntity s
    WHERE s.status = 'ACTIVE'
      AND s.searchText IS NOT NULL
      AND LOWER(s.searchText) LIKE LOWER(CONCAT('%', :keyword, '%'))
    ORDER BY s.createdAt DESC, s.id DESC
""")
List<ShowcaseJpaEntity> findActiveByKeywordFirstPage(
        @Param("keyword") String keyword,
        Pageable pageable);

@Query("""
    SELECT s FROM ShowcaseJpaEntity s
    WHERE s.status = 'ACTIVE'
      AND s.searchText IS NOT NULL
      AND LOWER(s.searchText) LIKE LOWER(CONCAT('%', :keyword, '%'))
      AND (s.createdAt < :cursorCreatedAt
           OR (s.createdAt = :cursorCreatedAt AND s.id < :cursorId))
    ORDER BY s.createdAt DESC, s.id DESC
""")
List<ShowcaseJpaEntity> findActiveByKeywordWithCursor(
        @Param("keyword") String keyword,
        @Param("cursorCreatedAt") Instant cursorCreatedAt,
        @Param("cursorId") Long cursorId,
        Pageable pageable);
```

**근거**:
- `LOWER(search_text)` + `LOWER(keyword)` — case-insensitive (한글은 영향 없음, 영문은 매칭률↑)
- `searchText IS NOT NULL` — backfill 미실행 행 명시 제외 (운영자가 backfill 안 했을 때 noisy 결과 회피)
- cursor 페이징 — 기존 `findAllWithCursor` 패턴 재사용
- ACTIVE 상태만 — HIDDEN/DELETED/SOLD 제외 (기존 정책 일관)

### keyword 정규화 (ADR-019 D1)

본 PR 시점:
- 클라이언트가 보낸 keyword 를 그대로 LIKE pattern 으로 사용
- DB 측 LOWER 로 case-insensitive
- 공백 정규화 (예: 다중 공백 단일화) 없음 — 사용자 입력 그대로
- 자모 분리 (한글 IME 중간 입력 검색) 없음 — ADR-019 §D5 후속

```java
@RequestParam(required = false)
@Size(min = 1, max = 100, message = "검색어는 1~100자")
String keyword
```

빈 문자열 (`?keyword=`) 은 `@Size(min=1)` 로 거부. 미제공 (`?keyword` 없음) 은 기존 list 동작.

### `ListShowcasesUseCase` 흐름

```java
PageInfo<ShowcaseListResult> list(String keyword, String pageToken, int size);

// 구현:
public PageInfo<ShowcaseListResult> list(String keyword, String pageToken, int size) {
    if (keyword == null || keyword.isBlank()) {
        return existingNoKeywordList(pageToken, size);  // 기존 흐름
    }
    Cursor cursor = decodeCursor(pageToken);
    List<Showcase> page = (cursor == null)
            ? showcasePort.findByKeywordFirstPage(keyword, size + 1)
            : showcasePort.findByKeywordWithCursor(keyword, cursor.createdAt, cursor.id, size + 1);
    return toPageInfo(page, size);
}
```

### ADR-019 §D2 — FULLTEXT 임계 + 모니터링

- N (showcase 행 수) < 10,000 — LIKE 풀스캔 허용. 평균 latency p99 측정 시작.
- N ≥ 10,000 도달 시 ADR-020 또는 ADR-019 v1.1 — FULLTEXT(n-gram) 인덱스 도입.
- Prometheus/actuator 알람: `mysql_global_status_innodb_rows_read{table="showcase"}` 또는 `showcase_count` metric 으로 임계 모니터링.

## 5. 단계

### Step 1: domain-port-and-repository

**작업**:
- `ShowcasePort.findByKeywordFirstPage` / `findByKeywordWithCursor` 메서드 추가
- `ShowcaseJpaRepository` `@Query` 메서드 신규
- `ShowcasePersistenceAdapter` 위임 구현
- 단위 컴파일 + archTest

**AC**: `./gradlew compileJava archTest`

### Step 2: usecase-and-service

**작업**:
- `ListShowcasesUseCase.list` 시그니처에 `String keyword` 추가
- `ListShowcasesService` 구현 — keyword null/non-null 분기
- 호출자 (ShowcaseController) 영향 — keyword 인자 전달

**AC**: `./gradlew compileJava test --tests "*ListShowcasesService*"`

### Step 3: controller-and-validation

**작업**:
- `ShowcaseController.list` 에 `@RequestParam(required = false) @Size(min=1, max=100) String keyword`
- `ListShowcasesUseCase.list(keyword, pageToken, size)` 호출

**AC**: `./gradlew compileJava test --tests "*ShowcaseControllerTest*"`

### Step 4: integration-tests

**작업**:
- `ShowcaseSearchKeywordIntegrationTest` 신규 (별도 클래스 — 기존 `ShowcaseServiceIntegrationTest` 와 격리)
- 케이스: catalog 한국어 매칭 / 직접 입력 매칭 / 영문 매칭 / 대소문자 무시 / NULL 행 제외 / cursor 페이징 / 빈 결과
- catalog 등록 → showcase 등록 → JpaRepository LIKE 검증

**AC**: `./gradlew test --tests "*ShowcaseSearchKeywordIntegrationTest*"`

### Step 5: cucumber-acceptance

**작업**:
- `showcase.feature` 에 `?keyword=` 시나리오 추가 (한국어 매칭, 결과 비공개 제외, 미매칭 0건)
- `ShowcaseStepDefinitions` 보강

**AC**: `./gradlew test --tests "*Cucumber*"`

### Step 6: adr-019-and-final

**작업**:
- `ADR-019-showcase-search-keyword-policy.md` 작성 — D1~D5
- `docs/spec/api-spec.md` 갱신 (있으면) — `?keyword=` 파라미터
- 최종 `./gradlew build`

**AC**: `./gradlew build`

## 6. 테스트 계획

- **단위**: JpaRepository `@Query` 정합성, ListShowcasesService keyword null/non-null 분기, controller `@Size`
- **통합**: catalog 등록 → showcase 등록 → keyword LIKE 매칭 검증 (한국어 / 영문 / 대소문자 / NULL 제외 / cursor)
- **인수 (Cucumber)**: `?keyword=머큐리얼` 시나리오, 결과 비공개 제외 검증
- **회귀**: 기존 list (keyword 미제공) Cucumber 통과 — 기본 동작 깨지지 않음

## 7. 완료 기준

```bash
cd backend
./gradlew build
test -f docs/architecture/adr/ADR-019-showcase-search-keyword-policy.md
```

추가:
- [ ] code-reviewer / architecture-reviewer / database-optimizer / test-writer Critical 0
- [ ] CodeRabbit 통과
- [ ] 운영 적용 직전: ADR-018 §D5 backfill 1회 실행 확인

## 8. 롤백 전략

- 컨트롤러 `keyword` 파라미터 제거 → 기존 list 동작
- ADR-018 의 search_text 컬럼은 그대로 유지 (PR-3 와 무관)

## 9. 의존 / 가정

- main 에 PR #74/#75/#76/#77/#78 머지 완료 (확인됨 — base `8ed5eef`)
- ADR-018 §D5 의 backfill 은 운영자 책임. 본 PR 머지 직전 운영자가 직접 실행 (PR-4 prerequisite)
- 운영 행 수 N < 10,000 — LIKE 풀스캔 허용 임계 가정. 도달 시 ADR-020 도입 필요.
