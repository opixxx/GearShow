# EXEC_PLAN: catalog-list-ux

- **Type**: feature
- **Status**: completed
- **Risk**: Safe
- **Created**: 2026-05-11 17:49
- **Branch**: feature/catalog-list-ux
- **Worktree**: /Users/opix/GearShow/../gearshow-catalog-list-ux
- **Port**: 9000

---

## 1. 목표 (Goal)

카탈로그 목록 화면에서 (1) 30개 페이지 제한으로 일부 아이템이 보이지 않는 문제를 무한 스크롤로 해결하고, (2) 리스트 항목의 표시 정보를 한국어 풀네임(`fullNameKo`) + `modelCode` 형태로 개선하여 brand 중복을 제거한다.

## 2. 범위 (Scope)

### In
- 백엔드: `CatalogItemListResult` 응답 DTO 에 `fullNameKo` 필드 추가
- 프론트:
  - `CatalogItemSummary` 모델에 `fullNameKo` 추가 (nullable)
  - `CatalogScreen` 에 `ScrollController` 기반 무한 스크롤 페이지네이션 도입 (`hasNext` / `pageToken` 활용)
  - `ListTile.title` = `fullNameKo ?? brand`, `subtitle` = `modelCode` (brand 중복 제거)
- 통합 테스트: list 응답에 `fullNameKo` 노출 1건 추가

### Out
- 카탈로그 무한 스크롤 외 화면(HomeScreen 등)의 동일 패턴은 손대지 않음
- BulkImport/Create/Update API 응답 변경 없음
- 카탈로그 상세 화면 표시 변경 없음 (이미 별도 fullNameKo 노출 중)

## 3. 변경 대상 (Affected)

- **application/**: `CatalogItemListResult`
- **adapter/**: 없음 (Mapper 영향 없음 — record 직접 매핑)
- **frontend**:
  - `frontend/lib/src/models.dart` (`CatalogItemSummary`)
  - `frontend/lib/src/screens.dart` (`CatalogScreen`)
- **test**:
  - `CatalogItemServiceIntegrationTest` — `fullNameKo` 노출 케이스 1건 추가

## 4. 접근 (Approach)

**백엔드 DTO**:

```java
public record CatalogItemListResult(
    Long catalogItemId,
    Category category,
    String brand,
    String modelCode,
    String officialImageUrl,
    String fullNameKo,        // 신규 (nullable)
    Instant createdAt
) {
    public static CatalogItemListResult from(CatalogItem item) {
        return new CatalogItemListResult(
            item.getId(), item.getCategory(), item.getBrand(),
            item.getModelCode(), item.getOfficialImageUrl(),
            item.getFullNameKo(),
            item.getCreatedAt());
    }
}
```

**프론트 페이지네이션**:

```dart
class _CatalogScreenState {
  final ScrollController _scrollController = ScrollController();
  final List<CatalogItemSummary> _items = [];
  String? _nextPageToken;
  bool _hasNext = true;
  bool _isLoading = false;
  Object? _error;

  void _resetAndLoad() { /* 필터 변경 시 _items, _nextPageToken 초기화 후 _loadMore() */ }
  Future<void> _loadMore() { /* hasNext && !isLoading 일 때만, listCatalogs(pageToken: _nextPageToken) 후 누적 + nextPageToken 갱신 */ }
  // scrollController.addListener — 임계 거리(예: 200px) 내에서 _loadMore() 호출
}
```

**ListTile 표시**:

```dart
title: Text(item.fullNameKo ?? item.brand, ...);
subtitle: Text(item.modelCode ?? '', ...);  // modelCode null 시 빈 문자열
```

**양보 불가 규칙**:
- 페이지네이션 진행 중 로딩 인디케이터 표시
- 페이지네이션 실패 시 사용자에게 에러 노출 + 재시도 가능
- 필터(`category`, `keyword`) 변경 시 누적된 `_items` 초기화

## 5. 단계 (Steps)

### Step 1: extend-backend-dto-with-fullNameKo

**읽어야 할 파일**:
- `backend/src/main/java/com/gearshow/backend/catalog/application/dto/CatalogItemListResult.java`
- `backend/src/main/java/com/gearshow/backend/catalog/domain/model/CatalogItem.java`

**작업**:
- `CatalogItemListResult` record 에 `fullNameKo` 필드 추가 (위치: `officialImageUrl` 뒤, `createdAt` 앞)
- `from(CatalogItem item)` 팩토리에 `item.getFullNameKo()` 매핑 추가

**AC**:
```bash
cd backend && ./gradlew compileJava
```

**금지사항**:
- `CatalogItemDetailResult` 등 다른 DTO 변경 금지 (이미 fullNameKo 노출 중)

### Step 2: integration-test-for-list-fullNameKo

**읽어야 할 파일**:
- Step 1 산출물
- `backend/src/test/java/com/gearshow/backend/catalog/application/service/CatalogItemServiceIntegrationTest.java`

**작업**:
- `ListItems` 또는 `ResponseExposure` 중첩 클래스에 `list_response_exposesFullNameKo` 시나리오 1건 추가
- 한국어 풀네임 가진 아이템 등록 → 목록 조회 → `result.data().get(0).fullNameKo()` 검증

**AC**:
```bash
cd backend && ./gradlew test --tests "*CatalogItemServiceIntegrationTest*"
```

### Step 3: frontend-summary-model-add-fullNameKo

**읽어야 할 파일**:
- `frontend/lib/src/models.dart` (`CatalogItemSummary`)

**작업**:
- `CatalogItemSummary` 에 `final String? fullNameKo` 필드 추가
- `fromJson` 에 `fullNameKo: json['fullNameKo'] as String?` 추가

**AC**:
```bash
cd frontend && flutter analyze --no-pub lib/src/models.dart 2>&1 | grep -E "models\.dart.*error" || echo "no errors"
```

### Step 4: frontend-catalog-infinite-scroll

**읽어야 할 파일**:
- Step 3 산출물
- `frontend/lib/src/screens.dart` (line 582~726 — `CatalogScreen`)
- `frontend/lib/src/api.dart` (line 145~168 — `listCatalogs`)

**작업**:
- `_CatalogScreenState` 에 다음 상태 추가:
  - `final List<CatalogItemSummary> _items = []`
  - `String? _nextPageToken`
  - `bool _hasNext = true`
  - `bool _isLoading = false`
  - `Object? _error`
  - `final ScrollController _scrollController = ScrollController()`
- `initState()` 에서 `_scrollController.addListener` 등록 + 첫 페이지 로드
- `dispose()` 에서 `_scrollController.dispose()`
- 카테고리 탭 / 검색 트리거 시 `_resetAndLoad()` 호출 (누적 초기화)
- 스크롤 끝에서 200px 이내일 때 `_loadMore()` (hasNext && !isLoading 가드)
- `_loadMore()` 는 `api.listCatalogs(... pageToken: _nextPageToken, size: 30)` 호출 후 `_items.addAll(...)`, `_nextPageToken = result.pageToken`, `_hasNext = result.hasNext`
- 빌드: `ListView.separated(itemCount: _items.length + (_hasNext ? 1 : 0))` 마지막 항목은 로딩 인디케이터 또는 에러 표시
- `ListTile.title` = `Text(item.fullNameKo ?? item.brand, ...)`, `subtitle` = `Text(item.modelCode ?? '', ...)`

**AC**:
```bash
cd frontend && flutter analyze --no-pub lib/src/screens.dart 2>&1 | grep -E "screens\.dart.*error" || echo "no errors"
```

**금지사항**:
- `ChatListScreen` / `HomeScreen` 등 다른 화면의 페이지네이션 패턴 손대지 마라. 이유: 본 PR 범위 외
- AppBar / Scaffold 구조 변경 금지. 이유: 본 PR 은 데이터 로딩·표시만

## 6. 테스트 계획 (Test Plan)

- **Happy Path**:
  - `CatalogItemServiceIntegrationTest` — list 응답에 `fullNameKo` 노출
  - dev 모드 시각: BOOTS 46건 모두 보임 / 스크롤 끝 가까이 도달 시 다음 페이지 자동 로드 / `fullNameKo` 가 있는 아이템은 title 에 한국어 노출
- **Unhappy Path**:
  - 페이지네이션 중 네트워크 실패 시 에러 노출 + 재시도 가능 (코드로 검증)
  - 카테고리 변경 시 이전 누적 데이터가 사라지고 새 결과로 대체

## 7. 완료 기준 (Done Criteria)

```bash
cd backend && ./gradlew build
```

- [ ] 위 통과
- [ ] flutter analyze 로 변경 파일 error 0
- [ ] dev 시각 검증: 스크롤로 BOOTS 46건 다 보임 / fullNameKo + modelCode 표시
- [ ] EXEC_PLAN Status `completed`

## 8. 롤백 전략 (Rollback)

해당 없음. DTO 신규 nullable 필드 추가 (기존 클라이언트 호환). 프론트 위젯 변경. 스키마 변경 없음. PR revert 즉시 가능.
