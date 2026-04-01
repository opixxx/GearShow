# GearShow 테스트 전략 (ATDD)

---

## 철학

인수 테스트(Acceptance Test)가 **1차 테스트 전략**이다. 실제 사용자 행위를 검증하고, 리팩토링 시 안전망 역할을 한다. 통합 테스트는 Adapter 계층의 DB 연동과 계층 간 협력을 검증한다. 단위 테스트는 복잡한 도메인 로직에 한해 선택적으로 작성한다.

---

## 테스트 전략 요약


| 계층        | 도구                      | 대상                       | 작성 시점 |
| :---------- | :------------------------ | :------------------------- | :-------- |
| 인수 테스트 | Cucumber + Testcontainers | 사용자 시나리오 (API 단위) | **항상**  |
| 통합 테스트 | JUnit 5 + Testcontainers  | Adapter, Service 계층      | **항상**  |
| 단위 테스트 | JUnit 5                   | 도메인 모델, 복잡한 로직   | 선택적    |

### 테스트 피라미드

```
        ┌─────────────┐
        │  인수 테스트   │  ← 사용자 관점, 전 구간 검증
        ├─────────────┤
        │  통합 테스트   │  ← Adapter/Service 계층, DB 연동 검증
        ├─────────────┤
        │  단위 테스트   │  ← 도메인 로직, 프레임워크 의존 없음
        └─────────────┘
```

---

## ATDD 워크플로우

### Step 1: Cucumber 시나리오 작성

```gherkin
@auth @smoke
Scenario: 기존 사용자가 카카오 로그인을 수행한다
  Given 이미 가입된 카카오 사용자가 존재한다
  When 카카오 인가 코드로 로그인을 요청한다
  Then 응답 상태 코드는 200이다
  And 응답에 액세스 토큰이 포함되어 있다
```

### Step 2: 테스트 실행 (반드시 실패해야 한다)

```bash
./gradlew test --tests "*CucumberIntegrationTest"
# 기대 결과: 스텝 정의가 없거나 로직이 구현되지 않아 실패
```

### Step 3: Step Definition 구현

```java
@Given("이미 가입된 카카오 사용자가 존재한다")
public void 기존_카카오_사용자_생성() {
    // 테스트 데이터 세팅
}
```

### Step 4: 프로덕션 코드 구현

아키텍처 패턴을 따른다:

1. Domain 모델 (model, vo, exception)
2. UseCase 인터페이스 + Service 구현
3. Adapter (Controller, PersistenceAdapter)

### Step 5: 테스트 실행 (반드시 통과해야 한다)

```bash
./gradlew test --tests "*CucumberIntegrationTest"
# 기대 결과: 모든 테스트 통과
```

### Step 6: 리팩토링

테스트가 통과하는 상태를 유지하며 코드를 개선한다.

---

## 시나리오 태그 전략


| 태그         | 목적                     | 예시                        |
| :----------- | :----------------------- | :-------------------------- |
| `@smoke`     | 핵심 경로, 빈번하게 실행 | 로그인, 쇼케이스 등록       |
| `@auth`      | 인증 도메인              | 로그인, 토큰 갱신, 로그아웃 |
| `@user`      | 사용자 도메인            | 프로필 조회/수정, 회원 탈퇴 |
| `@catalog`   | 카탈로그 도메인          | 카탈로그 등록/조회          |
| `@showcase`  | 쇼케이스 도메인          | 쇼케이스 CRUD, 이미지, 댓글 |
| `@model3d`   | 3D 모델 도메인           | 3D 모델 생성/조회           |
| `@edge-case` | 경계 조건                | 잘못된 입력, 에러 시나리오  |
| `@slow`      | 장시간 테스트            | 로컬 개발 시 제외           |

### 태그별 실행

```bash
# Smoke 테스트만 (빠른 피드백)
./gradlew test -Dcucumber.filter.tags="@smoke"

# 특정 도메인만
./gradlew test -Dcucumber.filter.tags="@auth"

# 느린 테스트 제외
./gradlew test -Dcucumber.filter.tags="not @slow"
```

---

## 단위 테스트 작성 기준

다음 경우에만 단위 테스트를 작성한다:

1. **상태 전이 로직** — Showcase, Transaction 등의 상태 전이 검증
2. **도메인 검증 로직** — 팩토리 메서드의 유효성 검증, 비즈니스 규칙
3. **복잡한 계산** — 가격 계산, 정렬 알고리즘

```java
// 예시: 상태 전이 검증은 단위 테스트에 적합
class ShowcaseTest {
    @Test
    @DisplayName("ACTIVE 상태의 쇼케이스를 SOLD로 변경하면 isForSale이 false가 된다")
    void markAsSold_setsForSaleToFalse() {
        // Given
        Showcase showcase = Showcase.create(1L, 1L, "테스트", ConditionGrade.A);

        // When
        Showcase sold = showcase.markAsSold();

        // Then
        assertThat(sold.getStatus()).isEqualTo(ShowcaseStatus.SOLD);
        assertThat(sold.isForSale()).isFalse();
    }
}
```

---

## Step Definition 작성 규칙

### 기존 Step 재사용

새 Step을 만들기 전에 기존 Step을 확인한다:

```bash
grep -r "@Given\|@When\|@Then\|@And" src/test/java/com/gearshow/backend/steps/
```

### ScenarioContext 활용

Step 간 상태 공유는 ScenarioContext를 사용한다:

```java
public class AuthStepDefinitions {

    private final TestApiClient apiClient;
    private final ScenarioContext context;

    public AuthStepDefinitions(TestApiClient apiClient, ScenarioContext context) {
        this.apiClient = apiClient;
        this.context = context;
    }

    @Given("인증된 사용자가 로그인되어 있다")
    public void 인증된_사용자_로그인() {
        // 로그인 수행 후 토큰 저장
        String accessToken = loginAndGetToken();
        apiClient.authenticate(accessToken);
        context.put("accessToken", accessToken);
    }
}
```

### 시간 의존 테스트 금지

```java
// BAD: 자정 근처에서 깨짐
LocalTime endTime = LocalTime.now().plusHours(1);

// GOOD: 고정된 안전한 값 사용
LocalTime endTime = LocalTime.of(23, 59);
```

---

## 테스트 데이터 관리

### 고유 식별자 사용

```java
String nickname = "테스터_" + System.currentTimeMillis();
String providerUserKey = "kakao-" + UUID.randomUUID().toString().substring(0, 8);
```

### 시나리오 단위 상태 격리

Cucumber의 `@ScenarioScope`로 시나리오마다 새로운 컨텍스트가 생성된다. ScenarioContext는 시나리오 종료 시 자동 소멸된다.

---

## Feature 파일 구조

```
src/test/resources/features/
├── auth/
│   ├── login.feature
│   ├── token-refresh.feature
│   └── logout.feature
├── user/
│   ├── profile.feature
│   └── withdraw.feature
├── catalog/
│   └── catalog-item.feature
├── showcase/
│   ├── create-showcase.feature
│   ├── update-showcase.feature
│   ├── delete-showcase.feature
│   └── showcase-comment.feature
└── model3d/
    ├── request-model.feature
    └── model-status.feature
```

---

## CI/CD 연동

```yaml
# GitHub Actions
- name: 빌드 및 테스트
  working-directory: backend
  run: ./gradlew build
```

CI에서는 `./gradlew build`로 컴파일 + 전체 테스트 + JaCoCo 커버리지 검증을 한 번에 수행한다.

---

## 커버리지 측정

라인 커버리지가 아닌 **시나리오 커버리지**를 기준으로 한다:

```bash
# 전체 시나리오 수 확인
grep -rc "Scenario:" src/test/resources/features/

# 도메인별 시나리오 수 확인
grep -rl "Scenario:" src/test/resources/features/ | xargs -I {} sh -c 'echo {} && grep -c "Scenario:" {}'
```

---

## 체크리스트

PR 제출 전 확인 사항:

- [ ]  모든 신규 기능에 Cucumber 시나리오가 있다
- [ ]  Happy Path + Edge Case 시나리오를 모두 포함한다
- [ ]  기존 Step을 가능한 재사용했다
- [ ]  로컬에서 테스트 통과: `./gradlew test`
- [ ]  시간 의존 로직이 없다
- [ ]  적절한 태그가 적용되었다 (@smoke, @도메인)
