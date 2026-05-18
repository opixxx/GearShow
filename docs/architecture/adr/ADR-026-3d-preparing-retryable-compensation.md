# ADR-026: 3D 파이프라인 — PREPARING 단계 retryable 실패 보상 전이 (PREPARING→REQUESTED)

- **상태**: Accepted
- **결정일**: 2026-05-18
- **관련 PR**: fix/3d-prepare-retryable-compensation
- **결정 주체**: GearShow Backend
- **관련 문서**: [3D 생성 파이프라인 설계 §8.1](../../research/2026-04-23-3d-generation-pipeline-design.md)
- **관련 ADR**: [ADR-011](./ADR-011-3d-pipeline-multilayer-idempotency.md), [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md), [ADR-025](./ADR-025-3d-generation-admission-queue.md)

---

## 1. 배경 (Context)

`PrepareWorkflowService.doPrepare` 는 `transitionToPreparingUnderLock`(TX1, `REQUESTED→PREPARING` 조건부 UPDATE — **커밋됨**) 후 `callTripoOrHandle → startGeneration`(Tripo 이미지 업로드 + `POST /task` = 과금 지점) 을 호출한다. 즉 **Tripo 호출은 항상 `current_step=PREPARING` 상태에서 일어난다**.

`startGeneration` 이 retryable 예외(`CallNotPermittedException`=circuit open, `ModelGenerationRetryableException`=Tripo 4xx/5xx 에러응답, `TripoSemaphoreTimeoutException`=세마포어 타임아웃)를 던지면 `@RetryableTopic`(`include={CallNotPermittedException, ModelGenerationRetryableException}`) 으로 라우팅되어 재발행된다. 그러나:

- 1차 시도가 이미 `REQUESTED→PREPARING` 을 커밋했으므로, 재발행분 소비 시 워크플로우는 PREPARING 이다.
- `prepare()` 의 `currentStep != REQUESTED` 가드(PR #96, 큐 오염 차단) — 또는 `transitionToPreparingUnderLock` 의 `affected=0` — 가 단락시켜 `doPrepare` 가 조기 return.
- 예외가 던져지지 않으므로 `ModelGenerationWorker` 가 `markProcessed` 호출 → **재시도 체인 종료**.
- 워크플로우는 PREPARING + pending 無 → `Reconcile.recoverPreparing` 이 `preparing-stuck-seconds`(운영 기본 60s) 경과 후 `markFailed(TX2_DB_FAILED)`.

**결과**: Tripo 의 모든 retryable 실패가 문서(`ModelGenerationWorker` Javadoc, 설계 §8.1)가 약속한 "5회 backoff 재시도(~15분 윈도우, circuit 복구 대기 겸용)"가 아니라 **"실제 1회 시도 → ~60초 후 FAILED(원인과 무관한 TX2_DB_FAILED 코드)"** 로 귀결된다. 일시적 Tripo 429 한 번에 사용자 생성 요청이 영구 실패한다. 입장 큐 드레인 재발행분(`prepareFromAdmissionQueue`)도 `doPrepare` 공유라 동일하게 영향받는다.

설계 §8.1 은 원래 이 동작을 `Worker catch RetryableException: 조건부 UPDATE PREPARING → REQUESTED (재시작 가능)` 로 **명시했으나 코드에 구현된 적이 없다**(보상 없이 rethrow 만). 본 ADR 은 그 미구현 설계를 완성하되, §8.1 스케치가 누락한 **과금 모호성**을 보강해 확정한다.

## 2. 결정 (Decision)

`callTripoOrHandle` 에서 **미과금이 확정**인 retryable 예외에 한해, `PREPARING→REQUESTED` **보상 조건부 UPDATE** 후 원래 예외를 **재throw** 한다. 재발행분은 REQUESTED 를 보고 정상 경로로 **실제 재시도**한다.

### 보상 조건부 UPDATE (ADR-012 양보불가 규칙 준수)

`ModelGenerationWorkflowPort.compensatePreparingToRequested` →

```
UPDATE model_generation_workflow
   SET current_step = 'REQUESTED', started_at = NULL,
       heartbeat_at = :now, updated_at = :now
 WHERE id = :id
   AND current_step = 'PREPARING'                       -- 내가 방금 만든 PREPARING 만
   AND tripo_task_id IS NULL                            -- TX2 미실행
   AND NOT EXISTS (tripo_pending_task WHERE workflow_id=:id)  -- pending 선저장 전
```

- ADR-012 의 "모든 상태 전이 UPDATE 는 조건부" 규칙을 **그대로 따른다**. 락 모델·기존 전이·멱등성 계층 미접촉. 역방향 보상 전이 1개를 **가산**할 뿐이다.
- `affected=1` → 보상 성공 → 호출자가 원래 예외 재throw → `@RetryableTopic` backoff.
- `affected=0` → 과금됨(task/pending 존재) 또는 다른 주체가 이미 전이/종결 → **재throw 금지**, 호출자 정상 return → `markProcessed`. 기존 "남이 처리함=affected=0" 의미와 일관.
- `started_at = NULL` 로 리셋해 실패한 attempt 의 시간이 lifetime-cap / stuck 윈도우에 합산되지 않게 한다(다음 정식 TX1 이 재-stamp).

### 안전(미과금 확정) vs 위험(과금 가능) 분류

| 예외 | 과금 | 처리 |
|---|---|---|
| `CallNotPermittedException`(circuit open) | 미과금(HTTP 미발생) | **보상 후 재throw** |
| `TripoSemaphoreTimeoutException`(=Retryable 하위) | 미과금(HTTP 전) | **보상 후 재throw** |
| `ModelGenerationRetryableException`(Tripo 4xx/5xx **에러응답** 수신) | 미과금(task 미생성) | **보상 후 재throw** |
| `ResourceAccessException`/`RestClientException`(`POST /task` I/O 타임아웃, 응답 유실) | **과금 가능** | 현행 유지(미보상) — DLT/Reconcile |
| `TripoApiException`(2xx code≠0/null body) | (현행) | 현행 유지(미보상) |
| `preservePendingTask`/TX2(`DataAccessException`) | **확정 과금** | 현행 유지 — pending+`Reconcile.recoverPreparing` 가 GENERATING 복구(이중 과금 없음) |

`tripo_task_id IS NULL AND NOT EXISTS pending` 두 WHERE 조건은 **과금된 워크플로우를 절대 REQUESTED 로 되돌리지 않기 위함**이다 — 위반 시 재발행이 `POST /task` 를 재호출해 이중 과금(Tripo `cancel`/`Idempotency-Key` 미지원, ADR-011 §④).

## 3. 고려한 대안 (Alternatives)

### A. 조건부 UPDATE 제거 후 분산 락만 신뢰 — 기각

C1 의 `affected=0` 단락을 없애려 조건부 UPDATE 를 제거하는 안. **기각**: Redisson 락은 lease 만료/Redis failover 시 split-brain 가능한 *경합 최적화*일 뿐 fencing 이 아니다(ADR-012). 조건부 UPDATE 는 (a) DB fencing, (b) terminal 상태 불변, (c) ADR-025 N1 이중 디스패치 dedup 최종 방어, (d) 상태머신 적법성을 동시에 수행. 제거 시 완료 모델 회귀·이중 과금.

### B. 비관적 락(`SELECT … FOR UPDATE`) 전환 — 기각

C1 은 동시성 버그가 아니라 상태머신 의미 버그다. 비관적 락으로 바꿔도 `if currentStep != REQUESTED` 체크가 그대로 남아 동일 재현. ADR-012 전면 교체 비용만 발생하고 C1 페이로프 0.

### C. TX1 을 Tripo 호출 *뒤로* 재배치(REQUESTED 유지) — 기각

REQUESTED 인 채 Tripo 호출하면 보상 불필요해 보이나, 워커가 `POST /task` 성공 직후·`tripo_task_id` 기록 전 크래시 시 REQUESTED 인 채 task_id 유실 → `Reconcile.warnRequested` 재park 가 재디스패치 → **이중 과금**. PREPARING + `tripo_pending_task` 선저장은 이 "과금 후 크래시"를 이중 과금 없이 복구하기 위한 장치다. 채택안(미과금 확정만 보상)은 이 복구 속성을 보존한다.

## 4. 결과 (Consequences)

### 긍정

- 미과금 확정 retryable(실무상 대다수: Tripo 다운→circuit, 429/5xx, 세마포어 포화)이 **실제로 5회·30s→480s backoff 재시도**. `@RetryableTopic` 윈도우가 circuit 복구 대기로도 동작(문서 약속 충족).
- 일반 `prepare()` 와 드레인 `prepareFromAdmissionQueue()` 가 `doPrepare`/`callTripoOrHandle` 공유 → 동시 회복.
- ADR-012/락 모델/멱등성 계층 미접촉(가산적). 롤백 trivial(코드 revert, 스키마·이벤트계약 무변경).

### 잔여 / 수용

- `POST /task` I/O 타임아웃(송신 결과 불명)·`preservePendingTask`/TX2 실패는 **과금 가능**이라 보상하지 않는다 — ADR-011 §④ 본질적 잔여(Tripo `cancel`/`Idempotency-Key` 미지원, stranded task 1회분 크레딧 손실은 운영 balance 모니터링으로 사후 인지). 본 ADR 은 이 잔여를 *해소하지 않고* 명시적으로 좁힌다(전체 → I/O 타임아웃 1종).
- **circuit-open 경유 우회 (잔여 범위에 포함)**: `tripo` circuit breaker 는 `record-exceptions` 미설정(`application-prod.yml`)이라 resilience4j 기본값상 모든 예외를 실패로 집계한다. 따라서 1차 attempt 의 `POST /task` I/O 타임아웃(과금 가능)이 누적되어 circuit 이 OPEN 되면, 재발행분의 `startGeneration` 은 `CallNotPermittedException` 을 던진다. 1차가 task_id·pending 미기록이면 보상 WHERE 가드(`tripo_task_id IS NULL AND NOT EXISTS pending`)를 통과해 보상(affected=1)→REQUESTED→`POST /task` 재호출 → **이중 과금 가능**. 즉 "circuit open=HTTP 미발생=미과금"은 *단일 호출 관점*에서만 참이고, circuit 가 직전 I/O 타임아웃 누적으로 열렸을 가능성은 위 "I/O 타임아웃 미보상" 잔여와 **동일 클래스**다. 본 ADR 의 이중 과금 차단 보장은 *단일 호출 관점*으로 한정되며, 이 우회 경로는 위 잔여 범위에 포함된다. 정공법(circuit `record-exceptions` 에서 `ResourceAccessException` 제외 + `/upload`↔`/task` 엔드포인트 taxonomy)은 후속 작업.
- 보상 UPDATE 자체가 `DataAccessException`(DB 일시 장애)으로 실패하면 그 예외가 원 retryable 예외를 가린 채 전파된다 — `@RetryableTopic include` 밖이라 DLT 직행. 단 보상이 커밋되지 않아 워크플로우는 PREPARING 유지 → `Reconcile.recoverPreparing` 가 백스톱(정합 안전, 데이터 손실 없음).
- 예외 taxonomy 정밀화(`/upload` vs `/task` 엔드포인트 구분으로 `POST /task` connect-refused=미과금 추가 회수, `ResourceAccessException` 세분, circuit `record-exceptions` 명시) 는 **후속 작업**.
- attempt 당 UPDATE 2회(TX1 + 보상) — 조건부 UPDATE 라 저비용, 외부 I/O 무추가.
- 재시도 시 `prepare()` 가 `admitOrPark` 재평가 → cap 초과면 재park(`parkIfAbsent(now)` → FIFO score 리셋). 기존 F6(repark FIFO 후미) 수용 범위, 본 ADR 신규 부작용 아님.
- `started_at = NULL` 리셋은 의도된 동작(실패 attempt 시간을 lifetime-cap 에서 제외)이나, Tripo 장기 장애로 보상↔재시도가 반복되면 매 attempt 마다 lifetime-cap 이 리셋되어 실제 경과 ≫ `workflow-max-lifetime-minutes`(5분) 인 워크플로우가 stuck 탐지를 우회할 수 있다. 현재 `@RetryableTopic attempts=5`(~15분 윈도우)가 상한이라 무한 루프는 아니나, attempts 상향·트래픽 급증 시 Reconcile stuck 윈도우 정합성 재검토 — **운영 관측 항목**.
- 의존 전제: 보상 JPQL 의 `NOT EXISTS` 서브쿼리는 `tripo_pending_task.workflow_id` 가 **PK**(surrogate 없음, `TripoPendingTaskJpaEntity @Id`)임에 의존해 PK 점 조회로 실행된다. surrogate 키 도입 등 엔티티 리팩토링 시 실행 계획이 풀스캔으로 퇴화하므로 회귀 주의 지점.

### 검증

`PrepareWorkflowServiceTest`(보상 affected=1 재throw / affected=0 미재throw / 세마포어 타임아웃 / NonRetryable 회귀) + `ModelGenerationWorkflowJpaRepositoryTest`(PREPARING+무 pending→affected=1·REQUESTED·started_at NULL / pending 존재→0 / GENERATING→0 / REQUESTED→0).
