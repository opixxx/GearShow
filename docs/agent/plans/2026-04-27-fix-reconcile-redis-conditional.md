# EXEC_PLAN: fix-reconcile-redis-conditional

- **Type**: fix
- **Status**: in_progress  <!-- pending | in_progress | completed | error | blocked -->
- **Updated**: 2026-04-28 — scope 확장 (Redis 컨테이너 도입 추가)
- **Risk**: Safe
- **Created**: 2026-04-27 22:58
- **Branch**: fix/fix-reconcile-redis-conditional
- **Worktree**: /Users/opix/GearShow/../gearshow-fix-reconcile-redis-conditional
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

`gearshow.redis.enabled=false` 환경에서 `app.reconcile.enabled=true` 면 Spring Boot 부팅이 실패하는 모순을 코드 레벨에서 차단한다. Reconcile 의 의미 자체가 Redis(`WorkflowPollQueuePort`) 의존이므로, 두 플래그가 **AND** 일 때만 Reconcile Bean 이 등록되도록 강제하여 CD 3회 연속 실패(PR #47, #48, idempotency refactor)를 해결한다.

## 2. 범위 (Scope)

### In
- `ReconcileScheduler` 의 `@ConditionalOnProperty` → `@ConditionalOnExpression` 으로 교체 (Redis enabled AND reconcile enabled)
- `ReconcileStuckWorkflowsService` 의 `@ConditionalOnProperty` → 동일 패턴으로 교체
- `application-prod.yml:75-82` 의 `reconcile.enabled: true` 옆에 의도 주석 추가 (Redis 도 켜야 실효 발생)
- 기존 ReconcileScheduler/Service 테스트가 어노테이션 변경에 깨지지 않는지 확인
- **(2026-04-28 추가) Redis 7 컨테이너를 `docker-compose.prod.yml` 에 도입** — 3D 파이프라인 좌표화 인프라 (분산 락 / DelayedQueue / Tripo 세마포어 / 향후 ZSET 대기열)
- **(2026-04-28 추가) backend 컨테이너 env 에 `REDIS_ENABLED=true` / `REDIS_HOST=redis` / `REDIS_PORT=6379` 주입** + `depends_on: redis: service_healthy` 체이닝
- **(2026-04-28 추가) CD 워크플로우 (`.github/workflows/cd.yml`) 의 EC2 배포 step 에서 backend 기동 전에 redis 도 함께 `up -d`**

### Out
- **`WorkflowPollQueuePort` No-op 구현체 추가** — Redis 도입으로 자연스럽게 해소 (구현체 항상 존재).
- **CD 헬스체크 시간/재시도 정책 변경** — 부팅이 정상화되면 자동 해결.
- **다른 Conditional 정합성 일제 점검** — 다른 Bean 들은 이미 No-op fallback 패턴이 있어 동일 문제 없음.
- **AWS ElastiCache 전환** — 단일 인스턴스 prod 단계엔 docker compose 의 redis 컨테이너로 충분. 다중 Worker 도입 시점에 ElastiCache 검토.
- **JVM-local Striped<Lock> fallback 추가** — Redis 도입 후엔 무관 (Redisson 가 항상 활성). deferred refactors #18 로 별도 트래킹.

## 3. 변경 대상 (Affected)

- **domain/**: 없음
- **application/**: `showcase/application/service/ReconcileStuckWorkflowsService.java` — 클래스 어노테이션만 변경
- **adapter/**: `showcase/adapter/in/scheduler/ReconcileScheduler.java` — 클래스 어노테이션만 변경
- **인프라 / 배포 (2026-04-28 추가)**:
  - `docker-compose.prod.yml` — `redis` 서비스 추가 + `backend.environment` 에 `REDIS_*` 주입 + `backend.depends_on.redis` 체이닝 + `gearshow-redis-data` 볼륨
  - `.github/workflows/cd.yml` — EC2 배포 step 에서 `up -d kafka redis` 로 redis 함께 기동
- **docs/**:
  - `backend/src/main/resources/application-prod.yml` — `reconcile` 블록 위에 주석 추가
  - 새 테스트 파일이 추가되면 `backend/src/test/...` 경로 명시

## 4. 접근 (Approach)

**핵심 결정**: `@ConditionalOnProperty` 한 항목만 검사하던 것을 **AND 표현식** 으로 강화.

```java
@ConditionalOnExpression(
    "${app.reconcile.enabled:false} and ${gearshow.redis.enabled:false}")
```

- 양쪽 기본값을 `false` 로 두어 명시적 활성화 없으면 비활성 (보수적 default)
- SpEL `and` 연산자 (Java `&&` 가 아님 — `@ConditionalOnExpression` 은 SpEL 파서 사용)
- 두 플래그 모순 자체가 발생 불가 → 의도 정합성 강제

**왜 No-op fallback 안 만드는가**: `WorkflowPollQueuePort` JavaDoc 에 "구현체 없는 환경 (예: 단일 인스턴스 로컬/테스트) 에서는 Bean 이 등록되지 않아 **Poller 전체가 비활성화된다**" 명시. Reconcile 도 Poller 와 같은 운명을 따라야 함이 설계 원칙. No-op 추가는 이 원칙 위반.

**양보 불가 규칙**:
- 두 플래그 중 하나라도 false 면 Reconcile Bean 미등록 (단일 인스턴스 prod 정합성)
- 기존 `gearshow.redis.enabled=true` + `app.reconcile.enabled=true` 환경에서는 동작 변화 없음 (다중 인스턴스 시나리오 보존)

## 5. 단계 (Steps)

### Step 1: reconcile-bean-conditional-strengthen

**읽어야 할 파일** (작업 전 파악):
- `backend/src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java` — 현재 어노테이션 위치와 import 확인
- `backend/src/main/java/com/gearshow/backend/showcase/adapter/in/scheduler/ReconcileScheduler.java` — 현재 어노테이션 위치와 import 확인
- `backend/src/main/java/com/gearshow/backend/showcase/application/port/out/WorkflowPollQueuePort.java` — JavaDoc 의 "Bean 미등록 → Poller 비활성화" 의도 재확인
- `backend/src/main/resources/application.yml:127-130` — `gearshow.redis.enabled` 기본값 (`${REDIS_ENABLED:false}`) 확인
- `backend/src/main/resources/application-prod.yml:74-82` — `app.reconcile.*` 설정 위치

**작업**:

1. `ReconcileStuckWorkflowsService` 클래스 어노테이션 변경:
   - 제거: `@ConditionalOnProperty(prefix = "app.reconcile", name = "enabled", havingValue = "true")`
   - 추가: `@ConditionalOnExpression("${app.reconcile.enabled:false} and ${gearshow.redis.enabled:false}")`
   - import 정리: `ConditionalOnProperty` 미사용 시 제거 + `ConditionalOnExpression` 추가

2. `ReconcileScheduler` 클래스 어노테이션 변경:
   - 동일 패턴 적용
   - import 정리

3. 클래스 JavaDoc 한 줄 추가/갱신: 활성화 조건이 "두 플래그 모두 true" 임을 명시

**AC (Bash로 표현)**:
```bash
cd /Users/opix/GearShow/../gearshow-fix-reconcile-redis-conditional/backend
./gradlew compileJava
# 어노테이션 의도가 코드에 반영됐는지 grep 으로 검증
grep -n "ConditionalOnExpression" src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java
grep -n "ConditionalOnExpression" src/main/java/com/gearshow/backend/showcase/adapter/in/scheduler/ReconcileScheduler.java
# 잔존 ConditionalOnProperty 가 reconcile 키워드와 함께 남아있지 않은지 확인
! grep -n "ConditionalOnProperty.*reconcile" src/main/java/com/gearshow/backend/showcase/application/service/ReconcileStuckWorkflowsService.java
! grep -n "ConditionalOnProperty.*reconcile" src/main/java/com/gearshow/backend/showcase/adapter/in/scheduler/ReconcileScheduler.java
```

**금지사항**:
- `WorkflowPollQueuePort` 에 No-op 구현체를 추가하지 마라. 이유: JavaDoc 에 명시된 설계 원칙 위반 (구현체 없을 때 Bean 미등록이 의도).
- `ObjectProvider<WorkflowPollQueuePort>` 로 받아서 부분 동작시키는 패턴도 도입하지 마라. 이유: Reconcile 의 핵심 동작 4개 중 3개가 직간접적으로 PollQueue 의존 → 부분 동작은 디버깅 어려움 + 운영 모호성 증가.
- `application-prod.yml` 의 `app.reconcile.enabled: true` 값을 변경하지 마라. 이유: 다음에 Redis 도입 시 함께 자동 활성화되어야 하므로 설정값은 의도대로 유지.

### Step 2: prod-yml-comment

**읽어야 할 파일**:
- `backend/src/main/resources/application-prod.yml` — 74-82 라인 (reconcile 블록)
- Step 1 산출물: 변경된 두 클래스 (Step 1 의 어노테이션과 일관된 표현으로 주석 작성)

**작업**:

`application-prod.yml` 의 `reconcile:` 블록 바로 위 주석을 다음 한 줄(또는 두 줄) 추가:

```yaml
  # ⚠️ Reconcile 활성화는 gearshow.redis.enabled=true 와 AND 조건이다.
  # 단일 인스턴스 prod (Redis 미사용) 에선 자동 비활성 — 어플리케이션 레벨에서 강제.
  reconcile:
    enabled: true
    ...
```

**AC**:
```bash
grep -B2 "^  reconcile:" /Users/opix/GearShow/../gearshow-fix-reconcile-redis-conditional/backend/src/main/resources/application-prod.yml | grep -i "redis"
```

**금지사항**:
- `enabled: true` 값을 false 로 바꾸지 마라. 이유: 코드가 AND 조건으로 강제하므로 prod 설정값은 의도(다중 인스턴스 시 활성)대로 유지.

### Step 3: spring-context-test

**읽어야 할 파일**:
- 기존 `ReconcileSchedulerTest`, `ReconcileStuckWorkflowsServiceTest` 가 있는지: `find backend/src/test -name "Reconcile*"` 로 확인
- 기존 테스트가 `@SpringBootTest` 로 Reconcile Bean 을 로드하는지 확인 — 로드한다면 `@DynamicPropertySource` 로 `gearshow.redis.enabled=true` 추가 필요
- `backend/src/test/.../WorkflowLockFallbackConfigTest.java` 같은 유사 패턴 (있다면) 참조
- Step 1 산출물: 변경된 어노테이션이 있는 두 클래스

**작업**:

1. **기존 테스트 영향 확인 + 보완**: `Reconcile*Test` 검색 → Spring 컨텍스트 로딩이 `app.reconcile.enabled=true` 만으로 됐다면, `gearshow.redis.enabled=true` + 필요시 Redis 어댑터 mock 도 같이 활성화. 영향 없으면 skip.

2. **새 회귀 테스트 1개 추가**: `backend/src/test/java/com/gearshow/backend/showcase/infrastructure/config/ReconcileConditionalTest.java`
   - 시나리오 A: `app.reconcile.enabled=true` + `gearshow.redis.enabled=false` → Spring 컨텍스트가 정상 기동 (Reconcile Bean 미등록)
   - 시나리오 B: `app.reconcile.enabled=false` + `gearshow.redis.enabled=true` → Reconcile Bean 미등록
   - 시나리오 C: 둘 다 true → Reconcile Bean 등록됨 (RedissonWorkflowPollQueueAdapter mock/embedded 필요할 경우 testcontainers 또는 @MockBean 활용)
   - 최소 시나리오 A 만이라도 추가 (이번 버그의 회귀 방지 핵심)

**AC**:
```bash
cd /Users/opix/GearShow/../gearshow-fix-reconcile-redis-conditional/backend
./gradlew test --tests "*ReconcileConditional*"
./gradlew test --tests "*Reconcile*"
```

**금지사항**:
- 시나리오 C 를 위해 진짜 Redis 컨테이너를 띄우지 마라 (이 PR scope 밖). 필요 시 `@MockBean WorkflowPollQueuePort` 로 충분.
- 기존 통과 중인 테스트를 무력화하지 마라 (`@Disabled`, `@Ignore`, skipTests 금지).

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: Step 3 시나리오 C — 두 플래그 모두 활성 시 Reconcile Bean 등록 + 기존 동작 유지
- **Unhappy Path**: Step 3 시나리오 A — Redis 비활성 + Reconcile 활성 조합에서 컨텍스트가 부팅 실패하지 않고, Reconcile Bean 만 미등록 (이번 버그 회귀 방지 핵심 케이스)
- **추가 검증**:
  - ArchUnit: 영향 없음 (어노테이션 변경만, 의존 방향 동일)
  - 기존 통합 테스트 회귀: `./gradlew test` 전체 통과 (특히 Reconcile 관련 기존 테스트)

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

모든 step 완료 후 다음이 모두 통과해야 `Status: completed` 로 마무리:

```bash
cd /Users/opix/GearShow/../gearshow-fix-reconcile-redis-conditional/backend
./gradlew build           # 컴파일 + 전체 테스트 + 커버리지(70%) + ArchUnit
./gradlew test --tests "*ReconcileConditional*"  # 신규 회귀 테스트
```

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건
- [ ] architecture-reviewer Critical 지적 0건
- [ ] database-optimizer Critical 지적 0건 (Repository 변경 없음 → 호출 불필요)
- [ ] EXEC_PLAN 의 Status 필드를 `completed` 로 갱신
- [ ] PR 본문에 CD 실패 이력(#47, #48, idempotency refactor) 명시 — 머지 후 다음 push 의 CD 통과로 검증

## 8. 롤백 전략 (Rollback)

### 코드 변경 (어노테이션 + yml 주석)
`git revert <commit>` 한 번으로 복구. 데이터 변경 없음, 마이그레이션 없음, 외부 계약 변경 없음.

### 인프라 변경 (Redis 도입 — 2026-04-28 추가)
세 가지 단계의 롤백 옵션:

1. **빠른 비활성화** (Redis 컨테이너는 살리되 backend 만 사용 안하기):
   - EC2 의 docker-compose.prod.yml 또는 .env 에서 `REDIS_ENABLED=false` 로 backend env 강제 → backend 재기동
   - → 단 이 경우 3D 폴링 흐름이 멎음 (research §3.4 — Redis 의존 경로 외 백업 경로 없음)

2. **Redis 컨테이너 자체 제거**:
   - `docker compose -f docker-compose.prod.yml stop redis && docker compose -f docker-compose.prod.yml rm -f redis`
   - 이 PR revert (compose / cd.yml 변경 되돌림)
   - 단 데이터 손실: redis 의 lock / queue 가 모두 사라짐. 진행 중 워크플로우는 Reconcile 도 같이 비활성이라 stuck 됨 (수동 정리 필요)

3. **PR 전체 revert**:
   - `git revert <merge-commit>`
   - 다시 부팅 실패 상태로 회귀 — 권장하지 않음

다음 push 의 CD 가 또 실패하면 옵션 1 (REDIS_ENABLED=false) 로 즉시 핫픽스 후 원인 조사.
