# EXEC_PLAN: refactor-reconcile-conditional-bean

- **Type**: refactor
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Caution → Risky 경계 (CD 부팅 정합성 직결)
- **Created**: 2026-04-28 16:49
- **Branch**: refactor/refactor-reconcile-conditional-bean
- **Worktree**: /Users/opix/GearShow/../gearshow-refactor-reconcile-conditional-bean
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

PR #50 이 도입한 `@ConditionalOnExpression("${app.reconcile.enabled:false} and ${gearshow.redis.enabled:false}")` 를 정통 Spring Boot 패턴 (`@Configuration` 클래스 + `@ConditionalOnBean(WorkflowPollQueuePort.class)` + `@ConditionalOnProperty(app.reconcile.enabled)`) 으로 교체한다. application 레이어 (`ReconcileStuckWorkflowsService`) 에서 인프라 키워드 `gearshow.redis.enabled` 직접 참조를 제거 (헥사고날 정합성 — 메모리 deferred_refactor #18). 행동 변경 0 — 모든 시나리오에서 활성화 결과 동일 (3D 파이프라인 동작 무영향, Reconcile 자체가 stuck 복구 전용 백그라운드라 정상 흐름과 분리).

## 2. 범위 (Scope)

### In
- 신규 파일: `backend/src/main/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileBeansConfig.java`
- 신규 파일 (구현 중 추가, architecture-reviewer MINOR 2 반영): `backend/src/test/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileBeansConfigIntegrationTest.java` — `@SpringBootTest + Testcontainers Redis` 로 prod 환경의 시나리오 #4 happy path 검증
- `application-prod.yml`: 운영자 가이드 코멘트의 stale `@ConditionalOnExpression` 참조 갱신 (구현 중 추가, code-reviewer MAJOR 1 반영)
- `ReconcileStuckWorkflowsService.java`: `@Service` + `@ConditionalOnExpression` 제거. POJO 화. `@RequiredArgsConstructor` 유지.
- `ReconcileScheduler.java`: `@Component` + `@ConditionalOnExpression` 제거. POJO 화. `@RequiredArgsConstructor` + `@Scheduled` 유지.
- `ReconcileConditionalTest.java`: 시나리오 #4 는 통합 테스트로 위임 (lightweight runner 가 prod 처리 순서 재현 부족), 시나리오 #1/#2/#3/#5 만 보존. mock 빈 셋 분리 (Nit 4 반영).
- `ReconcileBeansConfig` 의 클래스 JavaDoc 에 "왜 `@Configuration` 으로 분리했는지" + "옵션 2 채택 사유 (통합 테스트로 검증됨)" + "메모리 #18 후속" 명시.

### Out
- ADR 신설 (이번 PR 에선 보류 — Configuration 패턴 정합화는 ADR-009~012 의 무게 결정과 격이 다름)
- `RedissonWorkflowPollQueueAdapter` 등 Redis 어댑터의 조건 (`@ConditionalOnProperty(gearshow.redis.enabled=true)`) 변경 — 그쪽은 인프라 어댑터라 인프라 키워드 직접 참조가 정합
- 테스트 외 다른 테스트 파일 변경 (`ReconcileSchedulerTest`, `ReconcileStuckWorkflowsServiceTest`) — Mockito 기반 단위 테스트라 빈 등록 방식 변경에 무영향
- `application-prod.yml` 의 `app.reconcile.enabled` 값 변경 (현 false 그대로)

## 3. 변경 대상 (Affected)

- **domain/**: 없음
- **application/**: `ReconcileStuckWorkflowsService.java` (어노테이션 2개 제거 — `@Service`, `@ConditionalOnExpression`)
- **adapter/**: `ReconcileScheduler.java` (어노테이션 2개 제거 — `@Component`, `@ConditionalOnExpression`)
- **infrastructure/config/**: `ReconcileBeansConfig.java` 신규
- **test/**: `ReconcileConditionalTest.java` (5 시나리오 보존 + WorkflowPollQueuePort mock 빈 추가)
- **docs/**: 없음 (ADR 신설 안 함)

## 4. 접근 (Approach)

**핵심 변경의 의미**:
- 빈 등록 방식: 컴포넌트 스캔 (`@Service`/`@Component`) → 명시 등록 (`@Configuration` + `@Bean`)
- 활성화 조건: SpEL AND → Spring 표준 Conditional 조합
- 결과: 모든 시나리오 행동 동일 (Reconcile 활성/비활성 결과 표 변동 없음)

**왜 옵션 2 (Configuration + @Bean) 인가**:
- Spring Boot 공식 입장: `@ConditionalOnBean` 은 `@Configuration` 클래스 안에서만 사용 권장. 자동 스캔 빈 (`@Service`/`@Component`) 에 직접 붙이면 컴포넌트 스캔 순서가 비결정적이라 시나리오 #4 ("두 플래그 모두 활성 → 등록") 가 환경에 따라 깨질 수 있음.
- 옵션 2 는 ConditionEvaluator 가 Configuration 처리 단계에서 안정적으로 평가.

**`ReconcileBeansConfig` 시그니처 (시그니처 수준)**:
```java
@Configuration
@ConditionalOnProperty(prefix = "app.reconcile", name = "enabled", havingValue = "true")
@ConditionalOnBean(WorkflowPollQueuePort.class)
public class ReconcileBeansConfig {

    @Bean
    public ReconcileStuckWorkflowsService reconcileStuckWorkflowsService(
            ModelGenerationWorkflowPort workflowPort,
            TripoPendingTaskPort tripoPendingTaskPort,
            WorkflowLockPort workflowLockPort,
            WorkflowPollQueuePort workflowPollQueuePort,
            ApplicationEventPublisher eventPublisher,
            ReconcileProperties properties) { ... }

    @Bean
    public ReconcileScheduler reconcileScheduler(ReconcileStuckWorkflowsUseCase useCase) { ... }
}
```

`@EnableConfigurationProperties(ReconcileProperties.class)` 는 **불필요** — `GearShowApplication.java:10` 에 `@ConfigurationPropertiesScan` 이 글로벌 활성이라 자동 감지.

**양보 불가 규칙**:
- `ReconcileScheduler` 의 `@Scheduled` 어노테이션 보존 — 스케줄링은 빈 등록 후 Spring 의 `ScheduledAnnotationBeanPostProcessor` 가 처리
- 두 클래스의 `@RequiredArgsConstructor` 보존 — 생성자 자동 생성, `@Bean` 메서드에서 호출
- 두 클래스의 클래스/메서드 JavaDoc 보존 — 비즈니스 의미 설명은 그대로
- `@Slf4j` 등 의미적 어노테이션 변경 금지

**부팅 정합성 매트릭스 (불변)**:

| `app.reconcile.enabled` | `gearshow.redis.enabled` | `WorkflowPollQueuePort` 빈 | `ReconcileBeansConfig` | Reconcile 빈 |
|---|---|---|---|---|
| true | true | 등록 | 활성 | 등록 |
| true | false | 미등록 | `@ConditionalOnBean` false → 비활성 | 미등록 |
| false | * | * | `@ConditionalOnProperty` false → 비활성 | 미등록 |
| 미설정 | * | * | default false → 비활성 | 미등록 |

## 5. 단계 (Steps)

### Step 1: reconcile-beans-config-add

**읽어야 할 파일**:
- `backend/src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java` (현 의존성 6개 확인)
- `backend/src/main/java/com/gearshow/backend/showcase/adapter/in/scheduler/ReconcileScheduler.java` (현 의존성 1개 확인)
- `backend/src/main/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileProperties.java` (record 확인)

**작업**:
`backend/src/main/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileBeansConfig.java` 신규 작성.

- 클래스 어노테이션:
  - `@Configuration`
  - `@ConditionalOnProperty(prefix = "app.reconcile", name = "enabled", havingValue = "true")`
  - `@ConditionalOnBean(WorkflowPollQueuePort.class)`
- `@Bean public ReconcileStuckWorkflowsService reconcileStuckWorkflowsService(...)` — 6개 인자 (workflowPort, tripoPendingTaskPort, workflowLockPort, workflowPollQueuePort, eventPublisher, properties)
- `@Bean public ReconcileScheduler reconcileScheduler(ReconcileStuckWorkflowsUseCase useCase)` — 1 인자
- 클래스 JavaDoc: "왜 Configuration 으로 분리했는지 (Spring Boot `@ConditionalOnBean` 권장 사용처) + 메모리 #18 후속 + 활성화 매트릭스"

**금지사항**:
- `@EnableConfigurationProperties(ReconcileProperties.class)` 추가 금지. 이유: `@ConfigurationPropertiesScan` 글로벌 활성이라 중복.
- `@Bean` 메서드에서 `new` 호출 외 다른 로직 추가 금지. 이유: 순수 구성, 비즈니스 로직 0.
- ADR 작성 금지. 이유: Scope Out.

**AC**:
```bash
cd backend
./gradlew compileJava
test -f src/main/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileBeansConfig.java
grep -q "@ConditionalOnBean(WorkflowPollQueuePort.class)" src/main/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileBeansConfig.java
grep -q "@ConditionalOnProperty(prefix = \"app.reconcile\", name = \"enabled\", havingValue = \"true\")" src/main/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileBeansConfig.java
```

### Step 2: remove-class-level-annotations

**읽어야 할 파일**:
- Step 1 산출물: `ReconcileBeansConfig.java`
- `ReconcileStuckWorkflowsService.java`
- `ReconcileScheduler.java`

**작업**:
두 클래스에서 빈 등록/조건 어노테이션만 제거. 다른 어노테이션·필드·메서드·JavaDoc 보존.

- `ReconcileStuckWorkflowsService.java`:
  - `@Service` 제거 + import 제거
  - `@ConditionalOnExpression(...)` 제거 + import 제거
  - 보존: `@Slf4j`, `@RequiredArgsConstructor`, 클래스/메서드 JavaDoc 전부, 필드 6개 + 생성자
- `ReconcileScheduler.java`:
  - `@Component` 제거 + import 제거
  - `@ConditionalOnExpression(...)` 제거 + import 제거
  - 보존: `@Slf4j`, `@RequiredArgsConstructor`, `@Scheduled`, 클래스/메서드 JavaDoc 전부, 필드 1개 + 생성자

두 클래스의 클래스 JavaDoc 끝에 한 줄 추가 — `<p><b>등록</b>: {@link com.gearshow.backend.showcase.infrastructure.config.ReconcileBeansConfig}.</p>`.

**금지사항**:
- 메서드 본문 변경 금지. 이유: 행동 변경 0 원칙.
- `@RequiredArgsConstructor` 제거 금지. 이유: `@Bean` 메서드에서 생성자 호출.
- 다른 어노테이션 (`@Slf4j`, `@Scheduled`) 제거 금지.

**AC**:
```bash
cd backend
./gradlew compileJava
# @Service / @Component 잔존 0
! grep -E "^@Service$|^@Component$" src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java
! grep -E "^@Service$|^@Component$" src/main/java/com/gearshow/backend/showcase/adapter/in/scheduler/ReconcileScheduler.java
# @ConditionalOnExpression 잔존 0
! grep "@ConditionalOnExpression" src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java
! grep "@ConditionalOnExpression" src/main/java/com/gearshow/backend/showcase/adapter/in/scheduler/ReconcileScheduler.java
# application 레이어에서 gearshow.redis.enabled 키워드 잔존 0 (헥사고날 정합성 핵심 검증)
! grep "gearshow.redis" src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java
```

### Step 3: reconcile-conditional-test-update

**읽어야 할 파일**:
- Step 1 산출물: `ReconcileBeansConfig.java`
- `backend/src/test/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileConditionalTest.java`

**작업**:
5 시나리오 그대로 보존 + 다음 변경:

1. `withUserConfiguration(ReconcileTestConfig.class)` → `withUserConfiguration(ReconcileBeansConfig.class)` (또는 `ReconcileBeansConfig.class + WorkflowPollQueuePortMockConfig.class`) — 진짜 ReconcileBeansConfig 를 컨텍스트에 주입.
2. 시나리오 #4 ("두 플래그 모두 활성 → 등록") 만 `withUserConfiguration(WorkflowPollQueuePortMockConfig.class, ReconcileBeansConfig.class)` 같은 형태로 mock `WorkflowPollQueuePort` 빈을 추가 등록 — `@ConditionalOnBean` 이 통과되도록.
3. 시나리오 #1 ("Redis 비활성") 에서는 `WorkflowPollQueuePort` 빈을 제공 *하지 않아야* `@ConditionalOnBean` false 가 검증됨 — `withUserConfiguration(ReconcileBeansConfig.class)` 만.

핵심 트레이드오프: 5개 시나리오가 서로 다른 mock 빈 셋을 요구 → `withUserConfiguration` 을 시나리오별로 다르게 주거나, 한 `@TestConfiguration` 안에서 `WorkflowPollQueuePort` 빈을 *조건부* 로 만들어야 함.

권장 형태: 시나리오별로 별개 `@TestConfiguration` (`WorkflowPollQueuePortMockConfig` 가 `WorkflowPollQueuePort` mock 제공). 시나리오 #4 만 `ReconcileBeansConfig.class + WorkflowPollQueuePortMockConfig.class` 주입, 그 외는 `ReconcileBeansConfig.class` 만. JavaDoc 에 "왜 두 Config 인지" 명시.

`ReconcileStuckWorkflowsService` 의 다른 의존 (`ModelGenerationWorkflowPort`, `TripoPendingTaskPort`, `WorkflowLockPort`, `ApplicationEventPublisher`, `ReconcileProperties`) 도 mock 빈으로 제공 필요 — `@ConditionalOnBean` 평가는 통과해도 빈 자체 인스턴스화에서 DI 실패할 수 있으므로. 시나리오 #4 의 `@TestConfiguration` 이 모든 6개 의존을 mock 으로 제공.

**금지사항**:
- 5 시나리오 중 어느 하나라도 의도/표현 변경 금지. 이유: 회귀 회피.
- 시나리오 #4 에서 `WorkflowPollQueuePort` mock 빈을 제공하지 않으면 `@ConditionalOnBean` false → 시나리오 #4 가 거짓 통과 (둘 다 활성인데 미등록 으로 통과). 이걸 잡는 게 본 변경의 핵심 회귀 방어.
- mock 빈을 모든 시나리오에 일괄 제공 금지. 이유: `@ConditionalOnBean` 평가가 무의미해짐.

**AC**:
```bash
cd backend
./gradlew test --tests "com.gearshow.backend.showcase.infrastructure.config.ReconcileConditionalTest"
# 5개 모두 통과
```

### Step 4: full-build

**읽어야 할 파일**: 없음 (검증 단계)

**작업**: `./gradlew build` 실행. 컴파일 + 전체 테스트 + JaCoCo + ArchUnit.

**AC**:
```bash
cd backend
./gradlew build
# BUILD SUCCESSFUL
```

**금지사항**:
- 테스트 실패 시 `--tests` 로 우회 금지. 이유: 자가수정 루프 진입.
- `-x test` / `-x jacocoTestCoverageVerification` 사용 금지.

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: ReconcileConditionalTest 5 시나리오 모두 통과 — 빈 등록 결과가 옛 SpEL AND 와 동일.
- **Unhappy Path**:
  - 시나리오 #1 (Redis 비활성 + Reconcile 활성): `@ConditionalOnBean(WorkflowPollQueuePort.class)` 가 false → ReconcileBeansConfig 미등록 → Reconcile 빈 미등록.
  - 시나리오 #4 (둘 다 활성): `WorkflowPollQueuePort` mock 빈 명시 등록 → `@ConditionalOnBean` true → ReconcileBeansConfig 등록 → Reconcile 빈 등록.
- **추가 검증**:
  - ArchUnit 통과 (`./gradlew archTest`).
  - `./gradlew build` 전체 통과 (JaCoCo 70% + 통합 테스트).

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

```bash
cd backend
./gradlew build                            # 컴파일 + 전체 테스트 + 커버리지 + ArchUnit
./gradlew test --tests "com.gearshow.backend.showcase.infrastructure.config.ReconcileConditionalTest"
# application 레이어에서 인프라 키워드 잔존 0
! grep -rn "gearshow.redis\|@ConditionalOnExpression" src/main/java/com/gearshow/backend/showcase/application/
```

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건
- [ ] architecture-reviewer Critical 지적 0건 (헥사고날 정합성 검증)
- [ ] database-optimizer **호출 생략** (스키마/Repository 변경 없음)
- [ ] test-writer **호출 생략** (테스트 작성 완료)
- [ ] EXEC_PLAN 의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

해당 없음 (스키마/이벤트 계약/공개 API 변경 없음). 머지 후 회귀 발견 시 단순 revert PR 로 복구 가능.

부팅 회귀 발견 시 (`@ConditionalOnBean` 평가 이상으로 prod 부팅 실패):
1. `git revert <merge_commit>` 으로 `@ConditionalOnExpression` AND 조건 복귀
2. `application-prod.yml` 의 `app.reconcile.enabled` 가 false 로 유지되는 한 prod 영향은 0 (Reconcile 빈 자체가 미등록).

## 9. 변경 이력 (구현 중 결정)

- **옵션 2-A → 옵션 2 회귀**: 처음 옵션 2 (일반 `@Configuration`) 시도 시 lightweight `ApplicationContextRunner` 의 시나리오 #4 가 실패해 옵션 2-A (`@AutoConfiguration` + META-INF imports) 로 전환했다. architecture-reviewer 가 "lightweight 컨텍스트의 한계일 수 있다, prod 환경에선 옵션 2 도 동작할 가능성" 을 지적해 `@SpringBootTest` 로 검증 → 옵션 2 가 prod 환경에서 정상 동작 확인 → 옵션 2-A 의 자동설정 첫 도입 부담 회피, 옵션 2 정착.
- **plan scope 확장**: `ReconcileBeansConfigIntegrationTest` 신규 추가 (architecture-reviewer MINOR 2). `application-prod.yml` 운영자 가이드 코멘트 갱신 (code-reviewer MAJOR 1).
