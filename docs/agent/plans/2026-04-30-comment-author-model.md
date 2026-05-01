# EXEC_PLAN: comment-author-model

- **Type**: fix
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Safe
- **Created**: 2026-04-30 19:11
- **Branch**: fix/comment-author-model
- **Worktree**: /Users/opix/GearShow/../gearshow-comment-author-model
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

PR #53 (쇼케이스 댓글 응답에 작성자 닉네임/프로필 enrichment, 커밋 fe798d9) 에서 backend `CommentResult.Author` 응답에 대응하는 frontend Dart 모델 정의가 누락되어 Flutter 빌드가 깨진 상태를 복구한다. `frontend/lib/src/models.dart` 에 `ShowcaseCommentAuthor` 클래스를 추가하여 `ShowcaseComment.author` 와 `_CommentAuthorAvatar.author` 가 참조하는 타입을 제공한다.

## 2. 범위 (Scope)

### In
- `frontend/lib/src/models.dart` — `ShowcaseCommentAuthor` 클래스 정의 추가 (`userId`, `nickname`, `profileImageUrl` 필드 + `fromJson` factory)
- 클래스 위치: `ShowcaseComment` 클래스 직전 (의존 순서상 자연스러움)

### Out
- backend `CommentResult.Author` 형태 변경 — backend 는 이미 정상, frontend 누락만 복구
- `screens.dart` 사용처 수정 — 이미 `comment.author.nickname`, `author.profileImageUrl` 형태로 작성되어 있어 클래스 정의만 들어오면 컴파일 통과
- 다른 모델 누락 점검 — 본 PR 은 `ShowcaseCommentAuthor` 1건만 처리. 추후 누락 발견 시 별도 fix
- 테스트 코드 추가 — 단순 DTO 매핑이라 별도 테스트 케이스 불필요 (flutter analyze + 실제 빌드로 충분)

## 3. 변경 대상 (Affected)

- **domain/**: 없음 (frontend Dart, 백엔드 무관)
- **application/**: 없음
- **adapter/**: 없음
- **frontend/**: `frontend/lib/src/models.dart` 수정 (+20 라인)
- **docs/**: 없음

## 4. 접근 (Approach)

### backend 응답 형태 (이미 확인 완료)

`CommentResult.Author` record (`backend/src/main/java/.../showcase/application/dto/CommentResult.java`):
```java
public record Author(Long userId, String nickname, String profileImageUrl) {}
```
- `userId`: Long, non-null
- `nickname`: String, **nullable** (탈퇴/삭제 시 null)
- `profileImageUrl`: String, **nullable** (미설정 또는 탈퇴/삭제 시 null)

### frontend 사용처 (이미 확인 완료)

- `lib/src/models.dart:512` — `final ShowcaseCommentAuthor author;`
- `lib/src/models.dart:520` — `author: ShowcaseCommentAuthor.fromJson(authorJson)`
- `lib/src/screens.dart:1317` — `comment.author.nickname ?? '(알 수 없음)'`
- `lib/src/screens.dart:3723` — `final ShowcaseCommentAuthor author;`
- `lib/src/screens.dart:3727,3735` — `author.profileImageUrl`, `author.nickname`

### Dart 클래스 시그니처

```dart
class ShowcaseCommentAuthor {
  const ShowcaseCommentAuthor({
    required this.userId,
    required this.nickname,
    required this.profileImageUrl,
  });

  final int? userId;          // 탈퇴/삭제 후에도 보존되지만, 응답 형태 변형 대비 nullable
  final String? nickname;
  final String? profileImageUrl;

  factory ShowcaseCommentAuthor.fromJson(Map<String, dynamic> json) {
    return ShowcaseCommentAuthor(
      userId: (json['userId'] as num?)?.toInt(),
      nickname: json['nickname'] as String?,
      profileImageUrl: json['profileImageUrl'] as String?,
    );
  }
}
```

### 양보 불가 규칙

- **기존 `models.dart` 컨벤션 일치** — `final this.x` 스타일, `fromJson` factory, `(json['x'] as num?)?.toInt()` 패턴 등 주변 클래스(`UserProfile`, `ShowcaseComment`)와 동일하게
- **위치는 `ShowcaseComment` 직전** — `ShowcaseComment.author` 가 참조하는 타입은 의존 순서상 위에서 정의되는 게 자연스럽고, 인접 배치로 가독성 ↑
- **Dart linter 경고 0건** — `flutter analyze lib/src/models.dart` 통과
- **사용처 수정 X** — `screens.dart` 등 사용처는 이미 정상 작성되어 있음. 클래스 정의만 추가하면 통과

## 5. 단계 (Steps)

### Step 1: add-author-class

**읽어야 할 파일**:
- `/Users/opix/gearshow-comment-author-model/frontend/lib/src/models.dart` (495~525 라인 — `ModelGenerationRetryResult` 와 `ShowcaseComment` 사이)
- `/Users/opix/gearshow-comment-author-model/frontend/lib/src/screens.dart` (3720~3748 — `_CommentAuthorAvatar` 사용 패턴 재확인)
- `/Users/opix/gearshow-comment-author-model/backend/src/main/java/com/gearshow/backend/showcase/application/dto/CommentResult.java` (Author record 시그니처 재확인)

**작업**:

`frontend/lib/src/models.dart` 의 `ShowcaseComment` 클래스 직전에 `ShowcaseCommentAuthor` 클래스 추가. 시그니처는 §4 참조. 다른 부분은 건드리지 않는다.

**AC (Bash)**:
```bash
WT=/Users/opix/gearshow-comment-author-model
cd "$WT/frontend"

# 1) 클래스 정의 존재
grep -q "^class ShowcaseCommentAuthor {" lib/src/models.dart

# 2) fromJson factory 존재
grep -q "factory ShowcaseCommentAuthor.fromJson" lib/src/models.dart

# 3) flutter analyze 통과 — error 0건
flutter analyze 2>&1 | tee /tmp/analyze-all.out
! grep -E "^\s*error" /tmp/analyze-all.out
```

**금지사항**:
- `screens.dart` 의 사용처를 건드리지 마라. 이미 정상 — 클래스 정의 누락만 문제.
- 필드를 non-nullable 로 선언하지 마라. backend 가 탈퇴/삭제 시 nickname/profileImageUrl 을 null 로 내려준다 (CommentResult.java javadoc). `userId` 도 응답 변형 대비 nullable (`int?`).
- 다른 누락 모델을 함께 추가하지 마라. scope creep — 후속 발견 시 별도 fix PR.
- backend `CommentResult.Author` 시그니처를 수정하지 마라. 본 작업은 frontend 단독 복구.

## 6. 테스트 계획 (Test Plan)

- **Happy Path (자동, AC 1~3)**: `flutter analyze` 가 error 0건으로 통과. `ShowcaseCommentAuthor` 가 정의되어 있고 fromJson factory 가 존재.
- **Unhappy Path**: 별도 케이스 없음 — 단순 DTO 클래스 추가, fromJson 도 nullable 캐스팅 패턴이라 잘못된 입력은 모든 필드 null 로 처리됨 (기존 다른 fromJson 들과 동일 패턴).
- **수동 검증 (PR 머지 후)**: `bash scripts/run-frontend.sh --device=<id>` → 빌드 성공 + 댓글 화면에서 `nickname` / `profileImageUrl` 정상 표시 (이미 monitor 검증으로 빌드는 통과 확인됨, 머지 후 재확인)

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

```bash
WT=/Users/opix/gearshow-comment-author-model

cd "$WT/frontend"

grep -q "^class ShowcaseCommentAuthor {" lib/src/models.dart
grep -q "factory ShowcaseCommentAuthor.fromJson" lib/src/models.dart

flutter analyze 2>&1 | tee /tmp/analyze-all.out
! grep -E "^\s*error" /tmp/analyze-all.out
```

추가 정성 기준:
- [x] `flutter analyze` 본 PR 영향 범위 error 0건 (main 기준 4건 → 본 PR 적용 후 1건. 해결 3건 = `ShowcaseCommentAuthor` 관련 전부. 잔여 1건 = `MyApp` 사전 결함, 별도 fix PR 분리)
- [x] `screens.dart` 사용처 미변경 (`git diff screens.dart` 비어있음)
- [x] code-reviewer / architecture-reviewer / database-optimizer **스킵** — frontend Dart, 백엔드 헥사고날·DB 무관
- [x] EXEC_PLAN 의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

해당 없음 — frontend Dart 클래스 1개 추가. backend 응답 형태 변경 X, DB X, API 계약 X. 회귀 시 `git revert <commit>` 1줄. 다만 revert 하면 다시 빌드 깨지므로, 회귀 사유가 명확한 경우에만 (예: `ShowcaseCommentAuthor` 시그니처 자체에 문제가 발견된 경우 새 fix PR 로 교체).
