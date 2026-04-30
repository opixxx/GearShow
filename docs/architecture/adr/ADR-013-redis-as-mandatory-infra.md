# ADR-013: Redis 를 필수 인프라 의존으로 격상 — ConditionalOn fallback 폐기

- **상태**: Accepted
- **결정일**: 2026-04-30
- **결정 주체**: GearShow Backend
- **관련 ADR**: [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md) (분산 락 정책)
- **관련 PR**: refactor/redis-mandatory-infra

---

## 1. 배경 (Context)

GearShow 는 도입 초기부터 Redis 를 **선택적 의존**으로 다뤄왔다. 모든 Redis 어댑터/설정에 다음 패턴이 적용돼 있었다:

```java
@ConditionalOnProperty(name = "gearshow.redis.enabled", havingValue = "true")
public class RedissonWorkflowPollQueueAdapter { ... }
```

비활성 환경에선 Noop 또는 in-memory fallback 어댑터로 대체:

- `NoopTripoSemaphoreAdapter` — permit 검사 없이 action 통과
- `WorkflowLockFallbackConfig` — in-memory `ReentrantLock` map 으로 fallback

이 패턴의 두 의도:
1. **다중 인스턴스 한정 의미** — 분산 락/DelayedQueue/세마포어 등은 단일 인스턴스 prod 에선 동시성 충돌이 없으므로 in-memory 로 충분
2. **빠른 dev 셋업** — 신규 개발자가 Redis 없이도 백엔드 부팅 가능

### 트리거: Daily quota 도입

후속 작업으로 사용자별 하루 3D 생성 횟수 제한 (악성 사용자 비용 무한 증식 방지) 을 도입하려 한다. 이 기능의 본질:

- **단일 인스턴스 prod 에서도 항상 필요** — 다중 인스턴스 한정 기능이 아님
- **silent fallback 시 무방비** — Reconcile 의 "stuck 회복 안 됨" 같은 부분 손실이 아니라 **정상 path 자체가 비용 보호 X**
- **TTL 기반 자정 자동 초기화** — Redis INCR + EXPIRE 가 자연스러운 모델

기존 ConditionalOn 패턴 위에 daily quota 를 얹으면, Redis 비활성 환경 (단일 인스턴스 prod, local/dev) 이 모두 보호 X. 이는 본 기능의 의도와 정면 충돌.

## 2. 결정 (Decision)

**Redis 를 GearShow 인프라의 필수 의존으로 격상한다.** 구체적으로:

1. `gearshow.redis.enabled` yml 키 + 모든 코드 분기 제거
2. 모든 `Redisson*Adapter` / `RedissonConfig` 의 `@ConditionalOnProperty` 제거 → 무조건 빈 등록
3. `NoopTripoSemaphoreAdapter`, `WorkflowLockFallbackConfig` 클래스 삭제
4. 부팅 시 Redis 미연결 시 ApplicationContext 부팅 자체 실패 (fail-fast). silent fallback 금지.
5. `gearshow.redis.host` / `gearshow.redis.port` envvar 운영 강제. 빈 값이면 Redisson 생성 실패.

테스트 환경:
- 모든 `@SpringBootTest` 가 RedissonClient 부팅 의존 → Testcontainers Redis 자동 spin-up 으로 처리 (`RedisContainerSupport` abstract base class)
- 정적 `GenericContainer` 1회 spin-up + 모든 IT 가 상속 → IT 시작 시간 영향 최소화

## 3. 대안 (Alternatives Considered)

### A. DB fallback (스키마 + count(\*) 쿼리)

`model_generation_workflow` 에 `user_id` 컬럼 + 인덱스 `(user_id, created_at)` 추가 → daily quota 를 SQL count 쿼리로 구현. Redis 없이도 작동.

**기각 사유**:
- 본 결정은 daily quota 한정이 아니라 GearShow 인프라 정책 전체. DB fallback 은 daily quota 만 해결, 다른 Redis 의존 (분산 락, DelayedQueue, 세마포어) 의 fallback 패턴은 그대로 유지 → 정책 일관성 깨짐.
- 자정 자동 초기화 같은 TTL 모델을 SQL 로 구현하면 별도 cleanup 배치 필요 (좀비 누적). Redis TTL 한 줄로 끝나는 걸 복잡하게.
- `count(*)` 쿼리는 동시 race 에서 row-lock 없이 정확하지 않음. 본 결정 (Redis INCR atomic) 이 더 깔끔.

### B. ConditionalOn 유지 + daily quota 만 별도 정책

기존 패턴을 유지하면서 daily quota 만 "Redis 필수" 로 표시.

**기각 사유**:
- 한 시스템 안에 두 가지 인프라 정책 (Reconcile/락 = 선택적, quota = 필수) 공존 → 운영자 혼란.
- "Redis 가 일부 기능엔 필수 / 일부엔 선택" 메시지가 모호. 결국 운영 안전엔 Redis 가 필수가 됨.

### C. 본 결정 (Redis 필수화) ✅

**채택 사유**:
- 인프라 정책의 단순성·일관성 (모든 빈 무조건 등록, 부팅 fail-fast)
- daily quota 의 본질적 요구사항과 부합
- 운영 환경은 이미 docker-compose.prod.yml 에 redis 포함되어 있음 → 사실상 변경 사항 없음
- local/dev 도 docker-compose.yml 에 redis 포함 → 새 개발자 온보딩에 추가 부담 없음

## 4. 트레이드오프 (Consequences)

### 긍정

- **인프라 정책 일관성** — Redis 가 필수임을 코드/yml 에서 명확히 표현
- **fail-fast** — Redis 미연결을 부팅 시점에 즉시 인지. silent 무방비 위험 제거
- **단순성** — Conditional 분기 7곳 + Noop/Fallback 클래스 2개 + yml 분기 제거 → 코드 베이스 슬림화
- **Daily quota / 향후 Redis 기반 보안 기능 안전 도입 기반** — 후속 PR 들이 Redis fallback 고민 없이 진행 가능

### 부정

- **테스트 영향** — 모든 `@SpringBootTest` 가 Testcontainers Redis 의존. base class 도입 + 기존 IT 일괄 정리 필요. 정적 `GenericContainer` 공유로 IT 시작 시간 영향 최소화 (1회 spin-up).
- **운영 envvar 누락 시 부팅 실패** — `REDIS_HOST` 가 셋되지 않으면 Redisson 생성 실패 → 부팅 실패. CD 파이프라인에서 envvar 검증 필수 (이미 docker-compose.prod.yml 에 셋되어 있음).
- **Redis 장애 = 백엔드 장애** — Redis 단일 장애점. multi-AZ Redis 또는 sentinel 도입은 별도 결정. 현 시점엔 단일 Redis 운영 (개인 프로젝트 규모).

### 중립

- **롤백 가능성** — 본 PR 단일 머지 커밋 revert 면 모든 변경 원복. 운영 환경 영향 없음 (Redis 가 떠있어도 비활성 처리됨).

## 5. 영향 범위 (Impact)

| 영역 | 변경 |
|---|---|
| **Production 코드** | Conditional 7개 제거 (`RedissonConfig`, 3개 `Redisson*Adapter`, `AsyncPollingConfig`, `WorkflowPollingScheduler`, `ReconcileBeansConfig`) |
| **Production 코드 (DELETE)** | `NoopTripoSemaphoreAdapter`, `WorkflowLockFallbackConfig` |
| **Resources** | `application.yml` / `application-local.yml` / `application-prod.yml` 정리. `gearshow.redis.enabled` 제거 |
| **Test** | 신규 `RedisContainerSupport` base class. 4개 기존 IT 정리. `ReconcileConditionalTest` 의도 변경 또는 삭제 |
| **CI/CD** | `.github/workflows/cd.yml` 의 `REDIS_ENABLED` envvar 제거 (선택) |
| **메모리** | `project_3d_pipeline_phase1.md` 의 "Redis 비활성 fallback" 항목 정정 |

## 6. 후속 작업

- **PR-B (`model-generation-daily-limit`)**: 본 PR 머지 후 진행. Redis 기반 사용자별 일일 quota 카운터.
- **운영 모니터링**: Redis 메모리 사용량 / 연결 수 / 응답 시간 P99 메트릭 추가 (P1-H 관찰성 작업과 병합 가능)
- **Redis HA 검토**: 사용자 규모 확대 시 sentinel 또는 Cluster 도입. 현 단계엔 불필요.
