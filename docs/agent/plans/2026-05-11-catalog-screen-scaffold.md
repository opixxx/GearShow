# EXEC_PLAN: catalog-screen-scaffold

- **Type**: fix
- **Status**: in_progress  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Safe
- **Created**: 2026-05-11 17:28
- **Branch**: fix/catalog-screen-scaffold
- **Worktree**: /Users/opix/GearShow/../gearshow-catalog-screen-scaffold
- **Port**: 9000

---

## 1. 목표 (Goal)

`/catalog/search` 라우트로 직접 진입 시 `CatalogScreen`이 `No Material widget found` 에러로 깨지는 문제를 수정한다. `CatalogScreen.build`를 `Scaffold`로 감싸 Material ancestor를 보장한다.

## 2. 범위 (Scope)

### In
- `frontend/lib/src/screens.dart` 의 `CatalogScreen.build` 반환 트리를 `Scaffold(backgroundColor, body: SafeArea(...))` 로 감싸기

### Out
- 다른 화면 (HomeScreen 등 MainShell 4개 탭) 의 동일 패턴은 손대지 않음 — MainShell의 Scaffold 아래서만 보여지므로 현재 깨지지 않음. 본 fix 범위 외.
- 디자인 변경 없음. 색상은 기존 카탈로그 헤더와 동일한 `Color(0xFF111111)` 유지

## 3. 변경 대상 (Affected)

- **frontend**: `frontend/lib/src/screens.dart` (CatalogScreen.build 단일 함수)
- **domain/application/adapter**: 없음
- **docs**: 없음
- **test**: 없음 (Flutter 위젯 테스트가 현재 구성되어 있지 않음 — 추가 시 별도 PR)

## 4. 접근 (Approach)

`CatalogScreen.build` 의 반환:

```dart
return Scaffold(
  backgroundColor: const Color(0xFF111111),
  body: SafeArea(
    child: Column(...현행 그대로...),
  ),
);
```

**양보 불가 규칙**:
- 기존 child 트리는 그대로 유지. 인접 코드 청소·재구성 금지 (Surgical Changes 원칙)
- AppBar 추가 금지 — 카탈로그 화면이 자체 헤더("카탈로그" 텍스트)를 이미 갖고 있어 중복

## 5. 단계 (Steps)

### Step 1: wrap-catalog-screen-with-scaffold

**읽어야 할 파일**:
- `frontend/lib/src/screens.dart` line 582~716 (CatalogScreen)

**작업**:
- `CatalogScreen.build` 반환을 `Scaffold(backgroundColor: Color(0xFF111111), body: SafeArea(...))` 로 감싼다.
- child 트리(현재 SafeArea 직속 자식 Column)는 그대로 둔다.

**AC (Bash로 표현)**:
```bash
cd frontend && flutter analyze --no-pub lib/src/screens.dart 2>&1 | grep -E "screens\.dart.*error" || echo "no errors"
```

**금지사항**:
- AppBar 추가 금지. 이유: 자체 헤더 중복
- 다른 *Screen 클래스 손대지 마라. 이유: 본 fix 범위 외 (별개 후속 PR이 필요하면 별도 처리)
- 검색창/탭 UI 변경 금지. 이유: 본 fix는 Material ancestor 부재만 해결

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: dev 모드로 frontend 띄우고 홈에서 카탈로그 검색 진입 시 에러 없이 카탈로그 목록·검색창이 렌더되는지 시각 확인
- **Unhappy Path**: 해당 없음 (UI 트리 구조 fix)
- **추가 검증**: `flutter analyze` lib/src/screens.dart 에러 0

## 7. 완료 기준 (Done Criteria)

```bash
cd frontend && flutter analyze --no-pub lib/src/screens.dart 2>&1 | grep -E "screens\.dart.*error" || echo "no errors"
```

- [ ] 위 명령 출력이 "no errors"
- [ ] dev 모드로 띄워 카탈로그 검색 진입 시 정상 렌더 시각 확인
- [ ] EXEC_PLAN Status `completed`

## 8. 롤백 전략 (Rollback)

해당 없음. UI 위젯 단일 함수 변경. PR revert 만으로 즉시 복구.
