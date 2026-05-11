# EXEC_PLAN: catalog-list-filter

- **Type**: feature
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Safe
- **Created**: 2026-05-11 15:37
- **Branch**: feature/catalog-list-filter
- **Worktree**: /Users/opix/GearShow/../gearshow-catalog-list-filter
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

카탈로그 목록 조회 API (`GET /api/v1/catalogs`)에 카테고리 + 키워드 필터링을 추가하여, 프론트엔드가 의도하던 카테고리 탭/검색창이 실제로 백엔드 필터로 작동하게 한다. ADR-016 후속 PR-4의 키워드 검색 도입 단계로, FULLTEXT 인덱스는 도입하지 않고 LIKE 풀스캔으로 시작한다.

## 2. 범위 (Scope)

### In
- 백엔드 `GET /api/v1/catalogs`에 `category`(BOOTS/UNIFORM/null) + `keyword`(부분일치 LIKE) 쿼리 파라미터 추가
- `ListCatalogItemsUseCase` / `CatalogItemPort` / `CatalogItemJpaRepository` 시그니처 확장
- keyword 매칭 컬럼: `brand`, `model_code`, `full_name_ko`, `full_name_en` 4개에 OR LIKE
- 프론트 `CatalogScreen`에 "전체" 탭을 맨 앞에 추가, 기본 선택. "전체" 시 `category` 미전송 / 검색창 빈 값이면 `keyword` 미전송
- 단위 테스트 + Cucumber 시나리오

### Out
- `brand` 파라미터 (api-spec에는 있지만 프론트 미사용 — 후속 PR)
- FULLTEXT(n-gram) 인덱스 도입 (ADR-016 §후속 — N=10,000 임계 도달 시)
- api-spec.md의 `cursor` → `pageToken` 표기 정정 (별도 docs PR)
- 카탈로그 상태 필터링 옵션 (현재 ACTIVE 고정 유지)

### 후속 작업 (서브에이전트 리뷰에서 도출, 별도 PR)
- **CRITICAL — `catalog_item` 정렬·페이징 인덱스 추가**: `idx_catalog_item_status_created_id`, `idx_catalog_item_status_category_created_id`. 현재 카테고리 단독 필터·정렬 자체가 풀스캔+filesort. ADR-016의 N<10,000 LIKE 허용 전제가 인덱스 부재로 더 일찍 깨질 위험. 운영 DB ALTER 필요로 별도 PR.
- **`(:param IS NULL OR ...)` 분기 메서드 분할**: 인덱스 추가와 묶여야 의미 있음. plan 가변성 제거.
- **`ListCatalogItemsQuery` record 도입**: 다음 필터 추가 시점에 함께 처리 (Data Clump 해소).
- **잘못된 enum 입력 시 400 처리**: `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException` 핸들러 추가 (현재 500).

## 3. 변경 대상 (Affected)

- **domain/**: 없음 (도메인 모델 변경 없음, Category enum 그대로 사용)
- **application/**: `ListCatalogItemsUseCase`, `ListCatalogItemsService`, `CatalogItemPort`
- **adapter/**: `CatalogController`, `CatalogItemJpaRepository`, `CatalogItemPersistenceAdapter`
- **frontend**: `frontend/lib/src/api.dart`, `frontend/lib/src/screens.dart`
- **docs/**: 없음 (api-spec.md는 이미 해당 파라미터 정의됨 — Out 참조)
- **test/**: `ListCatalogItemsServiceTest`, Cucumber `features/catalog/*.feature`

## 4. 접근 (Approach)

**시그니처**:

```java
// ListCatalogItemsUseCase
PageInfo<CatalogItemListResult> list(Category category, String keyword, String pageToken, int size);

// CatalogItemPort
List<CatalogItem> findAllFirstPage(Category category, String keyword, int size);
List<CatalogItem> findAllWithCursor(Category category, String keyword,
                                    Instant cursorCreatedAt, Long cursorId, int size);
```

**양보 불가 규칙**:
- 기존 `findAllFirstPage(int size)` / `findAllWithCursor(Instant, Long, int)` 시그니처는 제거 — caller가 ListCatalogItemsService 하나뿐이므로 안전. 호환 시그니처(오버로드) 두지 않음.
- `category == null` → 전체. `keyword == null || blank` → 전체. 둘 다 null이면 기존 동작과 동일.
- `keyword`는 `%keyword%` 양방향 LIKE. 단 공백 trim 후 사용. SQL 와일드카드(`%`, `_`)는 입력값에서 escape하지 않음 (입력의 의도된 와일드카드를 그대로 허용 — 검색 UX 단순성 우선, 어차피 status=ACTIVE 범위 한정이라 부작용 작음).
- 정렬·status=ACTIVE·페이징 구조는 유지.
- JPQL `WHERE` 절은 `(:param IS NULL OR ...)` 패턴으로 옵셔널 조건 표현.

**프론트**:
- `_category` 타입 `String? _category = null` (null = 전체). 탭 3개: 전체(null), 축구화('BOOTS'), 유니폼('UNIFORM').
- `listCatalogs()` 호출 시 `_category` 그대로 전달, `_searchController.text.trim()` 빈 문자열이면 null 전달.

## 5. 단계 (Steps)

### Step 1: extend-application-layer

**읽어야 할 파일**:
- `backend/src/main/java/com/gearshow/backend/catalog/application/port/in/ListCatalogItemsUseCase.java`
- `backend/src/main/java/com/gearshow/backend/catalog/application/service/ListCatalogItemsService.java`
- `backend/src/main/java/com/gearshow/backend/catalog/application/port/out/CatalogItemPort.java`
- `backend/src/main/java/com/gearshow/backend/catalog/domain/vo/Category.java`

**작업**:
- `ListCatalogItemsUseCase.list` 시그니처를 `(Category category, String keyword, String pageToken, int size)`로 변경.
- `CatalogItemPort.findAllFirstPage` / `findAllWithCursor`에 `Category category, String keyword` 파라미터 추가. 위치는 size/cursor 앞.
- `ListCatalogItemsService`는 추가 로직 없이 인자 그대로 Port에 전달. Javadoc만 갱신.

**AC (Bash로 표현)**:
```bash
cd backend && ./gradlew compileJava
```

**금지사항**:
- Port 시그니처에 오버로드 추가 금지. 이유: caller가 단일하므로 사용처 일관성을 위해 단일 시그니처 유지.
- 키워드 정규화/형태소 분석 추가 금지. 이유: 본 PR은 단순 LIKE 도입까지가 범위 (ADR-016 후속).

### Step 2: extend-persistence-adapter

**읽어야 할 파일**:
- Step 1 산출물 (변경된 `CatalogItemPort`)
- `backend/src/main/java/com/gearshow/backend/catalog/adapter/out/persistence/CatalogItemJpaRepository.java`
- `backend/src/main/java/com/gearshow/backend/catalog/adapter/out/persistence/CatalogItemPersistenceAdapter.java`
- `backend/src/main/java/com/gearshow/backend/catalog/adapter/out/persistence/CatalogItemJpaEntity.java`

**작업**:
- `CatalogItemJpaRepository.findAllFirstPage` / `findAllWithCursor` JPQL을 다음 옵셔널 절로 확장:
  - `AND (:category IS NULL OR c.category = :category)`
  - `AND (:keyword IS NULL OR c.brand LIKE :keyword OR c.modelCode LIKE :keyword OR c.fullNameKo LIKE :keyword OR c.fullNameEn LIKE :keyword)`
- 키워드 파라미터는 어댑터 계층에서 `"%" + trimmed + "%"`로 감싸 전달 (Service는 raw keyword 전달, Adapter가 감싼다).
- `keyword`가 null/blank이면 어댑터에서 null로 전달.
- `CatalogItemPersistenceAdapter`는 신규 시그니처 매핑. 페이징·status 필터 유지.

**AC**:
```bash
cd backend && ./gradlew compileJava && ./gradlew test --tests "*ListCatalogItemsServiceTest*"
```

**금지사항**:
- JpaRepository 메서드 명 변경 금지 (테스트가 메서드명에 결합돼 있을 수 있음 — 기존 명 유지).
- FULLTEXT 인덱스/네이티브 쿼리 도입 금지. 이유: ADR-016 §후속에서 행 수 10,000 임계 도달 시 별도 PR.

### Step 3: extend-controller

**읽어야 할 파일**:
- Step 1·2 산출물
- `backend/src/main/java/com/gearshow/backend/catalog/adapter/in/web/CatalogController.java`

**작업**:
- `CatalogController.list`에 `@RequestParam(required=false) Category category` + `@RequestParam(required=false) String keyword` 추가.
- 호출부를 `listCatalogItemsUseCase.list(category, keyword, pageToken, size)`로 변경.
- 기존 size validation은 유지. category는 enum 자동 변환 (Spring이 처리). 잘못된 값은 400 표준 응답.

**AC**:
```bash
cd backend && ./gradlew compileJava
```

**금지사항**:
- `keyword`에 `@Size` 등 제약 추가 금지. 이유: 최소 PR 범위.
- 컨트롤러에 비즈니스 로직 추가 금지 (trim 등은 어댑터/서비스 책임).

### Step 4: tests

**읽어야 할 파일**:
- Step 1~3 산출물
- `backend/src/test/java/com/gearshow/backend/catalog/application/service/ListCatalogItemsServiceTest.java` (있다면)
- `backend/src/test/resources/features/catalog/` 디렉토리 기존 `.feature`
- `backend/src/test/java/com/gearshow/backend/common/integration/` Cucumber Steps

**작업**:
- 기존 `ListCatalogItemsServiceTest` 시그니처 변경 반영 + 다음 시나리오 추가:
  - category=BOOTS 필터 시 BOOTS만 반환
  - keyword가 brand에 부분일치
  - keyword가 fullNameKo에 부분일치
  - category + keyword 동시 적용
  - 둘 다 null이면 기존 동작 동일
- Cucumber `catalog_list.feature`(또는 신규)에 통합 시나리오 1~2개 추가:
  - `category=BOOTS&keyword=Nike` 호출 시 NIKE 축구화만 반환
  - 페이징과 필터 조합

**AC**:
```bash
cd backend && ./gradlew test
```

**금지사항**:
- `@MockBean` 사용 금지 (test-rules.md 준수). 이유: 전체 컨테이너 재기동.
- DB 직접 조작 금지 — 테스트 데이터는 도메인 팩토리/Repository로 적재.

### Step 5: frontend-all-tab

**읽어야 할 파일**:
- `frontend/lib/src/screens.dart` (CatalogScreen, line 582~716)
- `frontend/lib/src/api.dart` (listCatalogs, line 145~168)

**작업**:
- `_category` 타입을 `String? _category = null`로 변경 (null = 전체).
- 탭 3개로 변경: "전체"(`null`), "축구화"(`'BOOTS'`), "유니폼"(`'UNIFORM'`). 기본 선택 "전체".
- `_loadCatalogs()`에서 `category: _category` 그대로 전달 (null이면 `_uri` 쿼리에서 제외 — `api.dart`의 `_uri` 동작 확인 필요. 이미 null skip이면 추가 변경 없음).
- 검색창은 그대로 유지 — 빈 값이면 keyword null로 전송됨.

**AC**:
```bash
cd frontend && flutter analyze
```

**금지사항**:
- 페이지네이션 UI 변경 금지. 이유: 본 PR 범위 외.
- 검색창 디자인 변경 금지.

## 6. 테스트 계획 (Test Plan)

- **Happy Path**:
  - `ListCatalogItemsServiceTest.list_byCategory_returnsOnlyMatching`
  - `ListCatalogItemsServiceTest.list_byKeyword_matchesBrandOrFullNameKo`
  - Cucumber `catalog_list.feature` — `category=BOOTS&keyword=Nike` 200 응답 + 결과 필터링
- **Unhappy Path**:
  - keyword가 한국어 부분일치 케이스 (`머큐리얼`)
  - keyword 공백 입력 → 전체 반환 (trim 처리 검증)
  - (잘못된 category 값에 대한 400 응답은 별도 PR — `GlobalExceptionHandler`에 `MethodArgumentTypeMismatchException` 핸들러 부재로 현재 500 응답. 본 PR 범위 외)
- **추가 검증**: ArchUnit (헥사고날 의존 방향) — 기존 archTest 통과 유지

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

모든 step 완료 후 다음이 모두 통과해야 `Status: completed` 로 마무리:

```bash
cd backend && ./gradlew build
```

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건
- [ ] architecture-reviewer Critical 지적 0건
- [ ] database-optimizer Critical 지적 0건 (Repository JPQL 변경 있음 → 호출 필수)
- [ ] EXEC_PLAN의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

해당 없음. 스키마 변경·이벤트 계약 변경 없음. 신규 쿼리 파라미터는 모두 옵셔널이므로 기존 클라이언트 호환. 문제 발생 시 PR revert만으로 즉시 복구 가능.
