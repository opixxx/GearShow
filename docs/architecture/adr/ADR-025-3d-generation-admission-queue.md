# ADR-025: 3D 파이프라인 — 동시 생성 입장 큐 (admission queue)

- **상태**: Accepted
- **결정일**: 2026-05-16
- **관련 PR**: feature/3d-admission-queue
- **결정 주체**: opix
- **관련 문서**: [3D 생성 파이프라인 설계 v1.2](../../research/2026-04-23-3d-generation-pipeline-design.md) §3.4 · §6 · §7 · §8.4
- **관련 ADR**: [ADR-010](./ADR-010-3d-pipeline-table-split.md), [ADR-011](./ADR-011-3d-pipeline-multilayer-idempotency.md), [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md), [ADR-013](./ADR-013-redis-as-mandatory-infra.md)

---

## 1. 배경 (Context)

3D 생성 파이프라인은 사용자 요청을 `showcase.model-generation.request` Kafka 토픽으로 흘려보내고, `ModelGenerationWorker` 가 소비해 Tripo `POST /task`(과금 지점) 까지 진행한다. 트래픽 피크 시 동시에 생성 중인 Tripo task 수를 제어할 장치가 필요하다 — 비용(크레딧) 페이싱과 Tripo 측 부하 관리가 목적이다.

설계 문서 §3.4 는 이 목적의 `tripo:queue` (ZSET, score=enqueuedAt) 를 "⏳ 보류 (DelayedQueue 로 충분 시 제거)" 로 남겨두었고 §7 [5] 는 "Phase 2 도입 예정 (현재 미구현)" 으로 표기했다. 본 ADR 은 이 미결 항목을 정식 결정으로 격상한다.

### 사전 조사로 드러난 사실 (코드 검증, 2026-05-16)

설계 문서·메모리의 일부 가정이 현재 코드와 어긋나 있어, 본 결정은 **코드를 SoT** 로 삼는다:

1. **`tripo:semaphore` 는 "동시 생성 job 수" 게이트가 아니다.** `RedissonTripoSemaphoreAdapter.runWithPermit` 는 acquire → action → finally release 로, permit 을 *액션 실행 동안만*(수 초~수백 ms) 보유한다. Worker 의 Tripo 업로드+`POST /task` 구간(`TripoModelGenerationClient.startGeneration`)과 Poller 의 `GET /task` 1회(`PollWorkflowService`)를 감쌀 뿐이다. job 이 GENERATING 으로 수 분간 도는 동안 permit 은 0개다. 즉 세마포어 = "동시 Tripo HTTP 호출 ≤ 10" 인 **per-call API rate-limit** 이다.
2. **`poll:delayed-queue:main`(RDelayedQueue) 과 `tripo:queue`(ZSET) 은 별개 키·별개 목적**(설계 §3.4). 전자는 GENERATING 진행 폴링용으로 이미 구현·작동 중이며 본 결정은 전자를 건드리지 않는다.
3. **`ModelGenerationWorker` 는 `prepare(workflowId)` 단일 위임만 한다.** REQUESTED→PREPARING 전이는 그 안쪽 `PrepareWorkflowService.prepare()` 의 `validateSourceImages`(락 밖 S3 HEAD) → `transitionToPreparingUnderLock`(분산 락 안 조건부 UPDATE) 순서로 일어난다.
4. **Reconcile 은 REQUESTED 를 복구하지 않는다.** `ReconcileStuckWorkflowsService.warnRequested` 는 경고 로그만 남긴다(설계 §8.4 도 REQUESTED 를 "Outbox Relay 점검 신호"로만 규정). parked 워크플로우의 자동 복구 경로가 현재 전무하다.
5. 설계 문서 중 §8.1(backoff 수치), §8.4(Reconcile 복구 = Redrive 전략이지 직접 Tripo GET/S3/재POST 아님), §7[6](`tripo_trace_id` 미구현) 는 코드와 어긋난다. 본 결정은 이 섹션들을 SoT 로 쓰지 않는다.

## 2. 결정 (Decision)

동시 생성 job 수를 cap(기본 10, 설정화) 이하로 제어하는 **입장 큐(admission queue)** 를 도입한다.

### 기본 원칙

1. **게이트 지표 = DB COUNT.** 활성 작업 수 = `SELECT COUNT(*) FROM model_generation_workflow WHERE current_step IN ('PREPARING','GENERATING')`. DB 가 single source of truth — 워커 사망/누수가 자연 치유된다(상태는 DB 가 보유, Reconcile 이 stuck 을 종결).
2. **게이트 위치 = `PrepareWorkflowService.prepare()` 내부**, `validateSourceImages` 통과 직후 ∧ `transitionToPreparingUnderLock` 호출 직전. 즉 **분산 락 밖 · 과금 `POST /task` 전 · 영구실패 검증 후**.
3. **cap 초과 시 park + 정상 return.** `tripo:queue` Redis ZSET(`RScoredSortedSet`, score=enqueue epoch millis) 에 `parkIfAbsent` 후 워크플로우를 REQUESTED 로 둔 채 정상 반환. 호출자(`ModelGenerationWorker`)가 `markProcessed` 하여 Kafka 가 재전송하지 않는다. **Kafka 컨슈머 스레드를 절대 블로킹하지 않는다**(블로킹 = 컨슈머 lag → rebalance → 재전송 폭주, 2026-04-28 사고 클래스).
4. **주기 drainer 가 재투입.** `AdmissionQueueDrainScheduler` 가 fixedDelay 로, `countActive < cap` 이고 ZSET 이 비어있지 않으면 최소 score(가장 오래된) 항목을 pop 해 `PrepareWorkflowUseCase` 재호출. pop 후 워크플로우가 여전히 REQUESTED 인지 확인 후에만 진행(이중 처리/좀비 방어). tick 당 루프 상한을 둔다(스케줄러 스레드 starvation 방지).
5. **Reconcile 이 REQUESTED-stranded 를 복구한다 (correctness-critical).** park → `markProcessed` 로 Kafka 재전송이 끊기므로, ZSET/drainer 유실 시 이 복구가 유일한 잔여 안전망이다. `ReconcileStuckWorkflowsService` 의 기존 `warnRequested` 경로를, REQUESTED stuck ∧ ZSET 미포함 워크플로우를 `parkIfAbsent` 로 재-park 하도록 확장한다(Redrive 스타일 — Reconcile 이 직접 prepare 하지 않고 drainer 에 위임). 설계 §8.4 가 REQUESTED 를 "점검 신호" 로만 다룬 것을 **자동 복구로 확장하는 신규 동작**이다.
6. **`workflow:lock` 비관여.** 게이트는 락-free DB COUNT + ZSET 원자 연산(ZADD NX / ZPOPMIN)만 사용한다. 상태 전이 락(ADR-012)·Poller 락-free 원칙과 일관된다.
7. **기존 메커니즘 불변.** `tripo:semaphore`(per-call API rate-limit) 와 `poll:delayed-queue:main`(폴링) 의 동작·permit 수·키는 변경하지 않는다. 신규 게이트는 그 앞단의 직교(orthogonal) 제한이다.
8. **롤백 스위치.** `app.model-generation.admission.enabled=false` 시 **기능 도입 전 동작으로 완전 복원**된다 — (a) 게이트는 항상 통과(park 안 함), (b) `AdmissionQueueDrainScheduler` 빈 미등록(`@ConditionalOnProperty`), (c) **Reconcile 의 REQUESTED-stranded 재park 도 비활성**(경고 로그만 = 도입 전 `warnRequested` 동작). (c)가 없으면 drainer 부재 상태에서 Reconcile 이 ZSET 에 park 만 쌓아 영구 정체되므로 롤백 완결성을 위해 필수다. 설정 키: `app.model-generation.admission.{max-concurrent=10, drain-interval-ms=5000, enabled=true}`.

### 양보 불가 규칙

- 게이트는 `transitionToPreparingUnderLock` 의 `withLock` *밖* (락 진입 전) 에 위치. 위반 시 watchdog lease 충돌·락 점유 폭증(설계 §6.2).
- 게이트는 `validateSourceImages` *뒤* 에 위치. 위반 시 영구실패 job 까지 park 되어 큐 오염·실패 피드백 지연.
- park 분기는 예외 throw / sleep / 세마포어 대기 금지(컨슈머 블로킹). 분기 + return 만.
- 재투입(drainer)·복구(Reconcile) 는 `current_step == REQUESTED` 확인 후에만 prepare 진행.
- `parkIfAbsent` 는 이미 있으면 최초 score 유지(FIFO 공정성 보존).
- `ModelGenerationWorker` 의 `markProcessed` 시점/순서 변경 금지(ADR-011 §2.③).

## 3. 고려한 대안 (Alternatives)

### A. 장수명 Redis permit (POST /task 에서 acquire, 종료 시 release)

- 장점: 동시 job 수의 정확한(하드) 캡.
- 단점: 워커 사망 시 permit 영구 누수 → starvation. Redisson `RSemaphore` 는 owner/lease 개념이 없어 자동 회수 불가. `RPermitExpirableSemaphore` + Reconcile 갱신으로 보완 가능하나 복잡도 급증.
- 판단: **기각**. 분산 환경의 "장수명 락/permit + 누수" 안티패턴. DB COUNT 가 누수-free.

### B. 폴링+입장 통합 ZSET (RDelayedQueue 폐기)

- 장점: 큐 메커니즘 단일화.
- 단점: 작동 중인 적응형 폴링 경로(`poll:delayed-queue`)를 재작성 — Poller/Scheduler/Reconcile redrive 전면 회귀 위험. 두 큐는 목적(폴링 재시도 스케줄 vs 입장 순서)이 달라 score 시맨틱이 충돌.
- 판단: **기각**. 검증된 경로를 건드리는 비대칭 리스크.

### C. submit 시점 Kafka/ZSET 분기 (요청 인입 시 count<10 → Kafka, =10 → ZSET)

- 장점: 사용자 요청 문구에 직관적으로 부합.
- 단점: enqueue→consume 사이 count 가 변하므로 어차피 consume 시점 재검사 필요. ZSET 직행 경로가 outbox/Kafka 재시도·순서 보장을 우회.
- 판단: **기각**. Kafka 를 전송 SoT 로 유지하고 게이트를 consume 시점에 두는 것이 보장 측면에서 우월.

### D. 게이트를 `ModelGenerationWorker` 레벨 (prepare 호출 전)

- 장점: 컨슈머 경계에 게이트 — 위치가 명확.
- 단점: `validateSourceImages` 이전이라 소스이미지 누락 등 영구실패 job 까지 park → 큐 오염·실패 피드백 지연. doomed job 의 fail-fast(REQUESTED→FAILED) 상실.
- 판단: **기각**. 게이트는 validate 뒤·`PrepareWorkflowService` 안이 정확.

### E. 하드 캡(원자적 reservation) — Redis 카운터 + DB COUNT 하이브리드

- 장점: cap 초과 0 보장.
- 단점: 구현·검증 비용 최대, 카운터-DB 정합 동기화 부담. 본 시스템은 prod showcase 행 0 + 단일 인스턴스 단계라 과대 비용.
- 판단: **기각(현 단계)**. soft cap 수용. 피크 트래픽이 실측되면 후속 ADR 로 재평가.

## 4. 결과 (Consequences)

### 긍정

- 동시 생성 job 수가 cap 근처로 제어되어 Tripo 크레딧 페이싱·부하가 관리된다.
- DB 가 게이트 SoT — 워커 사망/재시작/누수가 Reconcile 로 자가 치유. permit 누수 0.
- Kafka 전송 보장(outbox·재시도·순서) 유지. 컨슈머 블로킹 없음.
- `poll:delayed-queue`·`tripo:semaphore` 무변경 — 회귀 표면 최소.
- 롤백이 설정 한 줄(`enabled=false`). 스키마 변경 0.

### 부정 / 리스크

- **soft cap**: `countActive()` 조회와 후속 PREPARING 전이가 원자적이지 않다. 동시 Worker 가 같은 count(예: 9)를 보고 둘 다 통과해 일시적으로 cap 을 소폭 초과할 수 있다. 단 기존 `tripo:semaphore` 가 Tripo API 호출 rate 는 하드 제한하므로 비용/레이트 폭주는 아니다. 하드 캡이 필요하면 대안 E 후속 ADR.
- **Reconcile 의존(correctness-critical)**: park → `markProcessed` 로 Kafka 재전송이 끊기므로, ZSET/drainer 유실 시 Reconcile REQUESTED-stranded 복구가 없으면 워크플로우가 REQUESTED 로 영구 정체. 이 복구는 옵션이 아니라 필수 구성요소다.
- **drain 지연(latency)**: drainer 가 fixedDelay 주기로만 도므로, 슬롯이 빈 직후~다음 tick(기본 5s) 사이 지연이 추가된다. 이벤트 기반 즉시 nudge 는 본 ADR 의 대안으로만 기록하고 미구현(신뢰 SoT 단순화) — 필요 시 후속.
- **재투입 시 재검증 비용**: drainer 가 `prepare()` 를 처음부터 재호출하므로 `validateSourceImages`(S3 HEAD 4회)가 재실행된다. cap 도달 빈도가 낮은 단계에서는 무시 가능.

### 검증

- 통합(Testcontainers Redis): cap 도달 상태 신규 요청 → `tripo:queue` park + 워크플로우 REQUESTED 유지 + Kafka ack. 슬롯 1개 종료 → drainer 가 최오래 항목 pop → PREPARING 전이. cap 경계.
- 통합: park 후 ZSET 강제 비움 → Reconcile 이 REQUESTED-stranded 감지 재-park.
- 단위: 게이트 3분기(`<cap` 진행 / `≥cap` park / `enabled=false` 통과), `parkIfAbsent` FIFO·idempotent, drainer 상한·REQUESTED-검증 skip.
- archTest: 신규 port out/adapter 위치, domain 무의존.

## 5. 참조

- 설계 문서: [`docs/research/2026-04-23-3d-generation-pipeline-design.md`](../../research/2026-04-23-3d-generation-pipeline-design.md) §3.4 (Redis 키 공간) · §6.1/§6.2 (락 범위) · §7 [5] (시퀀스) · §6.6/§8.4 (Reconcile 협조)
- 관련 ADR: ADR-012 (락-free Poller 원칙·조건부 UPDATE — 본 게이트가 동일 원칙 계승), ADR-013 (Redis 필수 인프라 — ZSET 어댑터 무조건 빈), ADR-011 (markProcessed 시점 — park 사슬의 전제)
- 참조 패턴: Admission Control, Redrive (Reconcile 위임 복구), Soft Concurrency Cap
