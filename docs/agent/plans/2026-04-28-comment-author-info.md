# EXEC_PLAN: comment-author-info

- **Type**: feature
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Caution
- **Created**: 2026-04-28 13:43
- **Branch**: feature/comment-author-info
- **Worktree**: /Users/opix/GearShow/../gearshow-comment-author-info
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

Showcase 상세의 댓글 응답에 작성자 닉네임·프로필 이미지를 함께 내려, 프론트가 더 이상 `사용자 #{authorId}` placeholder 와 임시 이모지 아바타를 노출하지 않게 한다.
N+1 없이 batch 조회로 enrich 한다.

## 2. 범위 (Scope)

### In
- `CommentResult` 응답 형태를 `api-spec §7-1` 의 `author: { userId, nickname, profileImageUrl }` 중첩 record 로 변경
- `showcase` BC 에 `UserReadPort` ACL 신설 (chat BC 와 동일 패턴, user 도메인 직접 참조 금지)
- `ListCommentsService` 에서 댓글 페이지 조회 후 작성자 IDs 배치 → `UserReadPort.getProfiles(...)` 한 번 호출로 enrich
- 탈퇴/삭제 사용자 placeholder (`nickname=null, profileImageUrl=null`) 정책 적용
- Frontend `ShowcaseComment` 모델·렌더링 갱신 (닉네임 표시 + 프로필 이미지 / fallback 첫 글자 아바타)
- 통합 테스트 (`ListCommentsService`) — 정상 / **DB에 없는 작성자(=하드 삭제된 ID)** placeholder 두 케이스
- Cucumber 인수 테스트 — 정상 케이스 1개 (`author` 객체가 응답에 포함되고 nickname 채워짐).
  - 탈퇴(WITHDRAWN) 사용자 placeholder 시나리오는 별도 PR 로 분리 — 현 정책상 WITHDRAWN status 사용자는 nickname 이 그대로 노출되며 (UserPort 가 status 필터 안 함), 이를 placeholder 화하는 건 도메인 정책 변경(별도 ADR 필요).

### Out
- 댓글 작성/수정/삭제 API 응답 형태 변경 (현재 스펙은 commentId 만 내려주므로 그대로)
- 프론트 회원 캐싱·Provider 도입 등 기반 인프라 변경
- API 응답에 `authorId` 평탄 필드를 호환 유지로 함께 내려주는 일 (스펙은 author 중첩만 정의 → 단일 클라이언트라 그대로 교체)
- **WITHDRAWN status 사용자 닉네임 익명화**: 현 동작은 탈퇴자 닉네임을 그대로 노출 (UserPort.findAllByIds 가 status 필터 없음). 이를 placeholder 화하는 정책 변경은 chat BC 동시 변경 + ADR 필요 → 별도 PR.

## 3. 변경 대상 (Affected)

- **domain/**: 없음 (도메인 모델 `ShowcaseComment` 변경 없음)
- **application/**:
  - `showcase/application/dto/CommentResult.java` — `Author` 중첩 record 추가, `authorId` 제거, `from(comment, author)` 시그니처
  - `showcase/application/dto/UserProfile.java` — 신규 (showcase BC 자체 DTO, chat BC 와 같은 이름·구조)
  - `showcase/application/port/out/UserReadPort.java` — 신규 (chat BC 의 동명 인터페이스를 그대로 복제)
  - `showcase/application/service/ListCommentsService.java` — `UserReadPort` 주입, 페이지 조회 후 batch enrich
- **adapter/**:
  - `showcase/adapter/out/user/UserReadAdapter.java` — 신규 (chat BC 의 동명 어댑터를 그대로 복제, user BC 의 `GetUserProfileUseCase` / `GetUserProfilesUseCase` 위임)
- **테스트**:
  - `backend/src/test/java/.../ListCommentsServiceIntegrationTest.java` — 신규 또는 보강 (정상 케이스 + 탈퇴 사용자 케이스)
  - `backend/src/test/resources/features/showcase-comment*.feature` — Cucumber 시나리오 2개 추가 (작성자 닉네임/이미지 노출, 탈퇴 사용자 placeholder)
  - `ShowcaseCommentStepDefinitions.java` — 응답 JSON `author.nickname` / `author.profileImageUrl` 검증 step 추가
- **frontend/lib/src/**:
  - `models.dart` `ShowcaseComment` — `authorId` 제거 → `author: ShowcaseCommentAuthor` 중첩 객체로 교체 (혹은 기존 `UserProfile` 모델 재사용 가능 여부 확인 후 결정)
  - `screens.dart` 라인 1307~1320 부근 — `_ownerEmoji(comment.authorId)` 제거, `comment.author.nickname` / `comment.author.profileImageUrl` 사용, 닉네임 첫 글자 fallback 아바타
- **docs/**:
  - 없음 (api-spec §7-1 가 이미 정답이라 기존 문서 그대로)

## 4. 접근 (Approach)

### 백엔드 BC 격리 패턴 (chat BC 의 검증된 구조를 복제)

```
showcase/application/port/out/UserReadPort         (interface)
    ↓ implemented by
showcase/adapter/out/user/UserReadAdapter          (delegates to user BC use cases)
    ↓ uses
user/application/port/in/GetUserProfilesUseCase    (existing, batch IN query)
```

- showcase 패키지는 `user` 도메인/엔티티 타입을 절대 import 하지 않는다 — 어댑터 안에서만 변환.
- `UserReadPort.getProfiles(List<Long> ids)` 의 반환 계약: 모든 입력 ID 가 키로 존재 (탈퇴/삭제는 `nickname=null, profileImageUrl=null` placeholder).

### CommentResult 시그니처 변경

```java
public record CommentResult(
        Long showcaseCommentId,
        Author author,
        String content,
        Instant createdAt
) {
    public record Author(Long userId, String nickname, String profileImageUrl) {}

    public static CommentResult from(ShowcaseComment comment, UserProfile profile) {
        return new CommentResult(
                comment.getId(),
                new Author(profile.userId(), profile.nickname(), profile.profileImageUrl()),
                comment.getContent(),
                comment.getCreatedAt()
        );
    }
}
```

`from(ShowcaseComment)` 1-arg 팩토리는 제거 — author 정보를 모르는 상태로는 만들 수 없게 한다 (컴파일 에러로 누락 강제).

### ListCommentsService enrichment 흐름

```
1) showcaseCommentPort 페이지 조회 → List<ShowcaseComment>
2) authorIds = comments.stream().map(getAuthorId).distinct().toList()
3) Map<Long, UserProfile> profiles = userReadPort.getProfiles(authorIds)  // 단일 IN 쿼리
4) results = comments.stream().map(c -> CommentResult.from(c, profiles.get(c.getAuthorId()))).toList()
```

`profiles.get(...)` 의 반환은 항상 non-null (포트 계약). placeholder 라도 author 객체는 만들어진다.

### 양보 불가 규칙

- **N+1 금지**: 어떤 경우에도 댓글 1건당 user 조회를 하지 않는다. `UserReadPort.getProfiles(List<Long>)` 단일 호출.
- **BC 격리**: `showcase` 패키지에서 `com.gearshow.backend.user.domain.*` import 금지 (ArchUnit 검증 대상).
- **placeholder 일관성**: 탈퇴 사용자도 응답에 포함된다 (필터링 금지). nickname/profileImageUrl 만 null.
- **read-only**: `@Transactional(readOnly = true)` 유지.

## 5. 단계 (Steps)

### Step 1: showcase-bc-userread-port-and-adapter

**읽어야 할 파일**:
- `backend/src/main/java/com/gearshow/backend/chat/application/port/out/UserReadPort.java` (복제 대상 인터페이스)
- `backend/src/main/java/com/gearshow/backend/chat/application/dto/UserProfile.java` (복제 대상 DTO — 정확한 위치는 chat 패키지 검색)
- `backend/src/main/java/com/gearshow/backend/chat/adapter/out/user/UserReadAdapter.java` (복제 대상 어댑터)
- `backend/src/main/java/com/gearshow/backend/user/application/port/in/GetUserProfileUseCase.java`
- `backend/src/main/java/com/gearshow/backend/user/application/port/in/GetUserProfilesUseCase.java`
- `backend/src/main/java/com/gearshow/backend/user/application/dto/UserProfileResult.java`

**작업**:
showcase BC 에 다음을 신설 (chat BC 와 동일 시그니처·동작):

1. `showcase/application/dto/UserProfile.java` — `record UserProfile(Long userId, String nickname, String profileImageUrl)`. Javadoc 한글.
2. `showcase/application/port/out/UserReadPort.java` — `interface { UserProfile getProfile(Long); Map<Long, UserProfile> getProfiles(List<Long>); }`. Javadoc 한글로 placeholder 계약 명시.
3. `showcase/adapter/out/user/UserReadAdapter.java` — `@Component`, user BC 의 `GetUserProfileUseCase` / `GetUserProfilesUseCase` 주입, `NotFoundUserException` catch → placeholder 반환, batch 조회는 `getProfiles(List<Long>)` 위임 후 누락 ID placeholder 채움.

**AC**:
```bash
cd /Users/opix/GearShow/../gearshow-comment-author-info/backend
./gradlew compileJava
./gradlew archTest
```

**금지사항**:
- `showcase` 패키지에서 `com.gearshow.backend.user.domain.*` 를 import 하지 마라. 이유: BC 격리 위반.
- 어댑터 안에서 user BC repository/JPA 직접 호출 금지. 이유: 어댑터는 user BC 의 application port (UseCase) 만 의존해야 한다.

### Step 2: comment-result-and-listservice-enrich

**읽어야 할 파일**:
- Step 1 산출물: `showcase/application/port/out/UserReadPort.java`, `showcase/application/dto/UserProfile.java`
- `backend/src/main/java/com/gearshow/backend/showcase/application/dto/CommentResult.java`
- `backend/src/main/java/com/gearshow/backend/showcase/application/service/ListCommentsService.java`
- `backend/src/main/java/com/gearshow/backend/showcase/domain/model/ShowcaseComment.java` (authorId getter 확인)

**작업**:
1. `CommentResult` 를 다음 형태로 교체:
   - `Author` 중첩 record `(Long userId, String nickname, String profileImageUrl)` 추가
   - 최상위 필드: `showcaseCommentId`, `author`, `content`, `createdAt` (이전의 `authorId` 평탄 필드 제거)
   - 정적 팩토리: `from(ShowcaseComment comment, UserProfile profile)` 만 유지 (1-arg `from` 제거)
2. `ListCommentsService.list(...)` 흐름 갱신:
   - 페이지 조회 결과에서 `authorId` distinct 모은 뒤 `userReadPort.getProfiles(...)` 한 번 호출
   - 각 댓글에 매핑된 `UserProfile` 로 `CommentResult.from(comment, profile)` 생성
   - `PageInfo.of(...)` 호출부의 키 추출 함수는 그대로 (createdAt + commentId)
3. `UserReadPort` 를 생성자 주입 (`@RequiredArgsConstructor` 유지).

**AC**:
```bash
cd /Users/opix/GearShow/../gearshow-comment-author-info/backend
./gradlew compileJava
./gradlew test --tests "*ListCommentsService*" --tests "*ShowcaseComment*"
./gradlew archTest
```

**금지사항**:
- `CommentResult.from(ShowcaseComment)` 1-arg 팩토리 유지 금지. 이유: author 누락 상태로 DTO 가 생성될 위험을 컴파일러가 막아야 한다.
- 댓글 1건마다 `userReadPort.getProfile(...)` 호출 금지. 이유: N+1.

### Step 3: tests-integration-and-acceptance

**읽어야 할 파일**:
- Step 2 산출물: `ListCommentsService.java`, `CommentResult.java`
- `backend/src/test/java/com/gearshow/backend/showcase/application/service/CommentServiceIntegrationTest.java` (인접 통합 테스트 패턴)
- `backend/src/test/java/com/gearshow/backend/steps/ShowcaseCommentStepDefinitions.java`
- `backend/src/test/resources/features/` 하위 댓글 관련 `.feature` 파일

**작업**:
1. **통합 테스트** (`ListCommentsServiceIntegrationTest` 신규 또는 기존에 추가):
   - 정상 케이스: 댓글 N건 + 활성 사용자 → 응답의 모든 `author.nickname`, `author.profileImageUrl` 이 user 도메인 값과 일치
   - 탈퇴/삭제 케이스: `authorId` 가 가리키는 user 가 존재하지 않음 → 해당 댓글의 `author.nickname == null`, `author.profileImageUrl == null`, `author.userId` 는 보존
   - N+1 검증 옵션 (선호): Hibernate Statistics or `@DataJpaTest` 의 query count assertion — 댓글 N개여도 user 조회 SQL 은 1회

2. **Cucumber feature**: 작성자 정보 노출 정상 시나리오 1개 추가
   - 가입 사용자가 댓글 작성 → 목록 조회 시 `data.data[0].author` 객체 존재
   - `author.userId`, `author.nickname` 비어있지 않음 (가입 닉네임)

3. **StepDefinitions**: 위 시나리오에 필요한 step 추가/재사용.

**AC**:
```bash
cd /Users/opix/GearShow/../gearshow-comment-author-info/backend
./gradlew test --tests "*ListCommentsService*" --tests "*ShowcaseComment*"
./gradlew test --tests "*CucumberTest*" -i 2>&1 | grep -E "(showcase-comment|FAILED|PASSED)" | head -20
./gradlew test
```

**금지사항**:
- 테스트 안에서 `@MockBean UserReadPort` 로 mock 하지 마라. 이유: 통합 테스트는 어댑터까지 포함한 실제 동작을 검증해야 한다 (단, user 데이터 setup 으로 시나리오 만든다).
- `authorId` 평탄 필드를 검증하지 마라. 이유: 이미 제거됨.

### Step 5: review-fixes-npe-rename-index-querycount

리뷰 결과(2026-04-28)에서 도출된 보강:
1. **NPE 가드**: `UserReadAdapter.getProfiles` 입력/결과 null 안전성 + `CommentResult.from` 시작부 `Objects.requireNonNull(profile)`
2. **클래스명 rename (chat + showcase 동시)**: `chat/.../UserReadAdapter` → `ChatUserReadAdapter`, `showcase/.../UserReadAdapter` → `ShowcaseUserReadAdapter`. 양쪽 모두 `@Component` qualifier 제거 (default bean name 분리됨). `ChatUserReadAdapterTest` 테스트 파일/클래스명 동반 변경.
3. **DB 인덱스**: `ShowcaseCommentJpaEntity` 에 `@Table(indexes=@Index(...))` — 컬럼 `(showcase_id, comment_status, created_at DESC, showcase_comment_id DESC)`. `docs/diagram/schema.md` SHOWCASE_COMMENT 섹션에 인덱스 표기 추가.
4. **쿼리 카운트 회귀 가드**: `CommentServiceIntegrationTest` 에 댓글 N건 시나리오 추가 — Hibernate `Statistics` 활성화하여 enrichment 후 user 조회 SQL 1회 검증.

### Step 4: frontend-models-and-rendering

**읽어야 할 파일**:
- Step 2 산출물(JSON 응답 형태): `CommentResult.java`
- `frontend/lib/src/models.dart` `ShowcaseComment` 클래스 (라인 ~503)
- `frontend/lib/src/models.dart` 의 `UserProfile` 클래스 (재사용 가능 여부 확인)
- `frontend/lib/src/screens.dart` 라인 1295~1340 (Showcase 상세 댓글 렌더링)
- `frontend/lib/src/screens.dart` `_ownerEmoji` 정의 (제거 대상)

**작업**:
1. `models.dart` `ShowcaseComment`:
   - `authorId` 필드 제거
   - `author` 필드 추가 — 타입은 다음 중 결정:
     - 기존 `UserProfile` 모델이 `(userId, nickname, profileImageUrl)` 와 호환되면 그대로 재사용
     - 호환 안 되면 `ShowcaseCommentAuthor` 신규 (단순 record-like 클래스)
   - `fromJson` 갱신: `json['author']` 객체 파싱
2. `screens.dart` 댓글 렌더링 (라인 1307~1320 부근):
   - `_ownerEmoji(comment.authorId)` 제거 → 닉네임 첫 글자 이니셜 또는 프로필 이미지
   - `comment.author?.profileImageUrl` 이 있으면 `CircleAvatar(backgroundImage: NetworkImage(...))`, 없으면 `CircleAvatar(child: Text(initial))`
   - 닉네임: `comment.author?.nickname ?? '(알 수 없음)'`
3. `_ownerEmoji` 가 댓글 외 다른 곳에서 사용 중인지 grep 으로 확인. 사용처 없으면 제거.

**AC**:
```bash
cd /Users/opix/GearShow/../gearshow-comment-author-info/frontend
flutter analyze
flutter pub get
# 빌드는 시간이 길어 생략 — analyze 통과로 정적 검증 충족
```

**금지사항**:
- API 응답 변경 후 프론트가 `authorId` 평탄 필드를 읽으려 하지 마라. 이유: 응답에 더 이상 없음 (JSON null → 모델 0 으로 떨어져 잘못된 사용자 표시).
- 프로필 이미지 URL null 체크 누락 금지. 이유: 탈퇴 사용자 케이스에서 NetworkImage(null) 호출 시 런타임 에러.

## 6. 테스트 계획 (Test Plan)

- **Happy Path**:
  - 통합: 활성 사용자 N명이 댓글 N개 작성 → 응답 모든 항목에 nickname / profileImageUrl 채워짐
  - Cucumber: `Given 활성 사용자 / When 댓글 목록 조회 / Then author.nickname 이 비어있지 않다`
- **Unhappy Path**:
  - 통합: 댓글 작성자 user 가 탈퇴 → 응답에 author.userId 는 보존, nickname/profileImageUrl 은 null
  - Cucumber: 동일 시나리오
- **추가 검증**:
  - ArchUnit: showcase → user.domain import 금지 (기존 룰 재확인)
  - N+1: 댓글 N건 + 활성 사용자 N명 케이스에서 user 조회 SQL 1회 (선호 — 가능하면 통합 테스트에 포함)

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

```bash
cd /Users/opix/GearShow/../gearshow-comment-author-info/backend
./gradlew build           # 컴파일 + 전체 테스트 + 커버리지(70%) + ArchUnit
cd ../frontend
flutter analyze
```

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건
- [ ] architecture-reviewer Critical 지적 0건
- [ ] database-optimizer Critical 지적 0건 (Repository 변경 없음 — 검토 결과만 확인)
- [ ] api-spec.md §7-1 의 응답 예시와 실제 응답이 일치하는지 수동 확인
- [ ] EXEC_PLAN 의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

- API 응답 형태가 평탄(`authorId`) → 중첩(`author.userId`) 으로 바뀌는 변경이지만 외부 클라이언트가 없는 상태이며, frontend 가 같은 PR 에서 동시 변경됨. revert 1번으로 양쪽 동시 되돌림 → 데이터 손실 없음, 스키마 변경 없음.
- 롤백 명령: `git revert <merge-commit>` 또는 PR revert 버튼.
- 사후 영향: 프론트가 다시 `사용자 #{authorId}` placeholder 로 돌아감 (UX 후퇴, 기능 손실 없음).
