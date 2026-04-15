# Test Rules

## 원칙

- **BDD 스타일 필수** — Given-When-Then 주석으로 테스트 의도를 명시
- **Happy + Unhappy** — 각 1개 이상 필수
- **구현과 병행 작성** — 나중에 몰아서 쓰지 않음
- **EXEC_PLAN 사전 명시** — 테스트 케이스는 1단계 플랜에서 미리 정의

## BDD 스타일 예시

```java
@Test
@DisplayName("쇼케이스 생성 — 제목이 공백이면 InvalidShowcaseException")
void create_빈_제목이면_예외() {
    // Given — 빈 제목과 정상 가격
    String emptyTitle = " ";
    int validPrice = 10_000;

    // When — 생성 시도
    ThrowingCallable action = () -> Showcase.create(emptyTitle, validPrice);

    // Then — 도메인 예외 발생
    assertThatThrownBy(action)
        .isInstanceOf(InvalidShowcaseException.class)
        .hasMessageContaining("제목");
}
```

## Happy Path / Unhappy Path 체크리스트

EXEC_PLAN의 "테스트 계획" 섹션에 미리 기입.

### Happy Path (최소 1개)
- 정상 입력 → 기대 출력
- 경계값 성공 케이스

### Unhappy Path (최소 1개 — 보통 여러 개)
- null / 빈 값 / 공백
- 범위 초과 / 음수 / 0
- 존재하지 않는 식별자
- 권한 없음
- 상태 전이 불가 (이미 완료된 주문 재시작 등)
- 외부 시스템 실패 (타임아웃, 5xx)

## 테스트 유형

### 단위 테스트 (도메인 모델, VO, Policy)
- 스프링 컨텍스트 **불필요**
- 순수 JUnit + AssertJ
- 빠름 (< 100ms/테스트)
- `domain/` 검증의 주력

### 유스케이스 테스트 (application/service)
- `@ExtendWith(MockitoExtension.class)` + Port Mock
- 외부 의존 전부 Mock 주입
- 비즈니스 흐름·예외 경로 검증

### 어댑터 통합 테스트 (adapter/out/persistence)
- `@DataJpaTest` + Testcontainers MySQL
- 실제 쿼리 동작 검증
- N+1, 인덱스, Fetch 전략 체크

### 인수 테스트 (Cucumber)
- 기존 `steps/`, `support/` 구조 활용
- 비즈니스 시나리오 단위
- 엔드투엔드 흐름 검증

## 네이밍 규칙

| 대상 | 네이밍 패턴 |
|---|---|
| 테스트 클래스 | `{ClassName}Test` (단위), `{ClassName}IntegrationTest` (통합) |
| 테스트 메서드 | 한글 `_` 구분 : `create_빈_제목이면_예외` |
| `@DisplayName` | "기능 설명 — 조건 → 결과" 형식 |

## 예외 검증

예외 테스트는 `assertThatThrownBy` 사용. 메시지·타입·원인 예외 모두 검증 가능.

```java
assertThatThrownBy(() -> service.create(cmd))
    .isInstanceOf(DuplicateEmailException.class)
    .hasMessageContaining("이미 사용 중")
    .hasRootCauseInstanceOf(SQLIntegrityConstraintViolationException.class);
```

ErrorCode 검증은 예외의 `status`·`message` 필드 직접 비교:

```java
assertThat(thrown.getStatus()).isEqualTo(ErrorCode.NOT_FOUND_USER.getStatus());
```

## 금지 사항

- `@Disabled`, `Assumptions.assumeTrue` 로 실패 테스트 회피
- `-DskipTests` 빌드로 검증 우회
- Happy Path만 있는 테스트 스위트 (Unhappy 누락)
- Mock over-use — 도메인 로직까지 Mock으로 대체하면 테스트가 허수가 됨
- 테스트 순서 의존 (`@TestMethodOrder` 남용)
- 공유 상태로 인한 테스트 간 오염

## 커버리지 목표

- 전체 커버리지 **70%** (build.gradle의 `jacocoTestCoverageVerification`)
- 제외 대상은 `jacocoExcludes` 리스트 참조 — 신규 UseCase 구현 시 제외 목록에서 제거

## 관련 규칙
- 헥사고날·SOLID·Anti-pattern : `coding-conventions.md`
- 예외 클래스 작성 규칙 : `exception-rules.md`
