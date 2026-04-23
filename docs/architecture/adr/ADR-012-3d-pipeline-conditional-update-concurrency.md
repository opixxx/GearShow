# ADR-012: 3D 파이프라인 — 선택적 분산 락 + 조건부 UPDATE 기반 동시성 제어

- **상태**: Accepted
- **결정일**: 2026-04-23
- **관련 PR**: docs/adr-010-011-012-3d-pipeline
- **결정 주체**: GearShow Backend
- **관련 문서**: [3D 생성 파이프라인 설계 v1.1](../../research/2026-04-23-3d-generation-pipeline-design.md)
- **관련 ADR**: [ADR-010](./ADR-010-3d-pipeline-table-split.md), [ADR-011](./ADR-011-3d-pipeline-multilayer-idempotency.md)

---

## 1. 배경 (Context)

3D 파이프라인은 하나의 `workflow_id` 에 대해 여러 주체가 동시에 접근할 수 있는 지점이 존재한다:

1. **Main Worker (Consumer)** — Kafka 메시지 수신 후 상태 전이 TX 수행
2. **Poller** — DelayedQueue 로부터 꺼내 Tripo 상태 조회 → 상태 갱신
3. **Downloader** — Tripo SUCCESS 응답 후 S3 미러링 → 완료 전이
4. **Reconcile 배치** — stuck 감지 후 복구

서버 수평 확장 (multi-instance) 및 blue-green 배포 중 rebalance 순간에는 같은 `workflow_id` 에 대한 복수 진입이 이론적으로 가능하다. 이 때 방어가 필요한 시나리오:

- 같은 workflow 에 **두 워커가 Tripo POST 를 중복 호출** → 이중 task 생성
- 한 워커의 TX 커밋 중 다른 워커가 선행 → 상태 꼬임
- Reconcile 이 살아있는 워커의 진행을 "stuck" 으로 오판 → 중복 복구

초기 설계는 모든 상태 전이 구간을 **Redisson 분산 락** 으로 감싸는 방향이었다. 그러나 설계 논의 중 다음 문제가 드러났다:

1. **외부 I/O 가 락 안에 들어가면 lease 만료** — Tripo upload · S3 업로드 등 수십초 이상 소요되는 네트워크 I/O 를 락 안에 두면 Redisson watchdog 갱신 실패 시 split-brain 위험.
2. **Rate limit 대기가 락 안** — Tripo 세마포어 대기 시간이 락 점유 시간을 잡아먹음.
3. **Poller 까지 락을 걸면 과도** — Poller 가 하는 일은 Tripo GET (부작용 없음) + 조건부 UPDATE 뿐. DB MVCC 로 충분히 방어되는 영역에 분산 락을 거는 것은 오버 엔지니어링.

리뷰 질문: "DelayedQueue 에서 폴링하는 것도 락으로 잠가야 하는 이유가 있어?"

이에 대한 분석 결과: **Poller 의 모든 race 시나리오가 조건부 UPDATE + Tripo GET 의 멱등성으로 해결된다**. 불필요한 분산 락은 Redis 장애 전파 · 복잡도 증가만 유발.

## 2. 결정 (Decision)

3D 파이프라인의 동시성 제어를 **선택적 분산 락 + 조건부 UPDATE** 로 일원화한다.

### 기본 원칙

1. **락은 "상태 전이 TX" 에만** — Main Worker 의 TX1 (REQUESTED→PREPARING), TX2 (PREPARING→GENERATING), TX_final (→COMPLETED), 그리고 Reconcile 배치의 복구 TX 에만 Redisson 락 적용.
2. **모든 외부 I/O 는 락 밖** — S3 GET/PUT, Tripo upload/POST/GET 은 반드시 락 해제 후 수행. 락 점유 시간은 TX 지속 시간(수~수십 ms 수준) 으로 한정.
3. **Poller 는 락 없음** — Tripo GET + 조건부 UPDATE 만으로 동시성 해결.
4. **모든 상태 전이 UPDATE 는 조건부** — `WHERE current_step = 'EXPECTED_PREV'` 절을 반드시 포함. `affected_rows == 0` 인 경우 "이 주체가 아는 상태가 더 이상 유효하지 않음" 으로 해석하고 해당 경로를 종결.

### 조건부 UPDATE 패턴

```sql
-- ❌ 단순 UPDATE (상태 꼬임 방치)
UPDATE model_generation_workflow
   SET current_step = 'GENERATING', tripo_task_id = ?
 WHERE id = ?;

-- ✅ 조건부 UPDATE (선행 주체가 이미 전이시켰으면 무효화)
UPDATE model_generation_workflow
   SET current_step = 'GENERATING', tripo_task_id = ?, heartbeat_at = NOW()
 WHERE id = ?
   AND current_step = 'PREPARING';
```

`affected_rows == 0` → 현재 상태 재조회 → 분기:
- 이미 더 진행된 상태면 내 작업 무효화, Tripo task_id 폐기.
- 더 이전 상태면 데이터 일관성 오류 → 로그 + Alert.

### Reconcile vs Worker 협조

Reconcile 배치는 **Main Worker 와 같은 락 키** (`workflow:lock:{workflowId}`) 를 공유한다. Worker 가 쥐고 있으면 Reconcile 은 자연스럽게 대기/skip, 락 해제 후에도 `heartbeat_at` 재검증으로 오판 방어.

### heartbeat 컬럼

외부 I/O 진행 중 주기 갱신. Reconcile 은 `heartbeat_at` 기준으로 stuck 판정.

### External State Verification

특정 상태(DOWNLOADING) 의 복구 시 DB 상태가 아닌 **S3 객체 존재 여부** 를 우선 신뢰. `s3.headObject("gearshow-models/{workflowId}.glb")` 존재 시 재업로드 스킵, 곧바로 COMPLETED 전이.

### 양보 불가 규칙

- **모든 상태 전이 UPDATE 는 조건부** (`WHERE current_step = 'EXPECTED_PREV'`). 단순 UPDATE 금지.
- Tripo `POST /task`, `GET /task`, `POST /upload`, S3 GET/PUT 은 **항상 락 밖**. 위반 시 lease 만료 리스크.
- Poller 에 Redisson 락 사용 금지. 단일 로직에 양방향(lock + 조건부 UPDATE) 배제.
- Reconcile 은 **Main Worker 와 같은 락 키** 사용. 별도 락 키 사용 금지 (협조 깨짐).
- `heartbeat_at` 갱신은 **외부 I/O 직전 + 중간 주기** 로 배치. Reconcile 오판 방지.

## 3. 고려한 대안 (Alternatives)

### A. 전 구간 Redisson 락 (락 안에서 외부 I/O)

- 장점: 구조가 단순 — "락 잡고 전부 처리 후 해제".
- 단점:
  - 외부 I/O 지속 중 lease 만료 → split-brain 시 Tripo 이중 POST · S3 이중 업로드
  - Rate limit 세마포어 대기가 락 안에 들어가 락 점유시간 폭증
  - Redis 장애 시 워커가 수십 초~분 단위로 멈춤 (lease 갱신 실패)
- 판단: **기각**. 분산시스템의 "락 안에서 외부 I/O" 안티패턴.

### B. 락 완전 제거 (조건부 UPDATE 만)

- 장점: 가장 단순. Redis 의존 감소.
- 단점:
  - Main Worker 의 상태 전이 + 외부 I/O 사이 **rebalance 순간 중복 진입** 시 조건부 UPDATE 만으로는 race 존재 (둘 다 `PREPARING→GENERATING` 을 시도, 하나만 성공, 그러나 둘 다 Tripo POST 를 이미 호출함).
  - 워커 여러 대 확장 시 서로의 진행 가시성 없음.
- 판단: **부분 채택**. Poller 에만 적용. Main Worker 상태 전이는 A-2 (POST 후 TX2 전 크래시) 방어를 위해 최소한의 락이 필요.

### C. DB row lock (`SELECT FOR UPDATE`) 단독

- 장점: Redis 불필요. DB 트랜잭션과 lock 수명 1:1.
- 단점:
  - 멀티 JVM 간 상호 배제는 DB 락으로도 가능하나, **Reconcile ↔ Worker 협조** 시 "살아있는 Worker 는 heartbeat 갱신 중, Reconcile 은 대기" 같은 협조 시나리오에서 `SELECT FOR UPDATE` 만으로는 "다른 주체가 작업 중인지" 를 빠르게 판단하기 어렵다 (결국 row lock 경합으로 블로킹되거나 NOWAIT 으로 즉시 실패하는 식).
  - 배포 중 rebalance 순간 짧은 시간 동안 두 JVM 이 같은 메시지를 consume 하면 `SELECT FOR UPDATE` 진입 경합은 순차 직렬화하지만, 직전 외부 I/O 는 이미 양쪽에서 시작됐을 수 있음.
- 판단: **기각 (단독 사용)**. Redisson 의 "락 획득 실패 즉시 skip" 패턴이 rebalance 순간의 중복 진입에 더 자연스러움.

### D. 상태 머신 엔진 (Saga / Temporal / Cadence)

- 장점: 상태 전이 · 복구 · heartbeat 를 엔진이 담당. 직접 구현 불필요.
- 단점: Phase 1 범위에 과함. 운영 인프라 추가. 팀 학습 곡선.
- 판단: **Phase 3 이후** 운영 복잡도가 증가하면 재평가.

### E. Poller 까지 Redisson 락 적용 (초기 설계안)

- 장점: 구조 일관성 (모든 상태 전이 구간에 락).
- 단점: 전술한 오버 엔지니어링. Poller 는 부작용 없는 GET + 조건부 UPDATE 만 하면 되고, 중복 호출 피해는 "Tripo GET 1회 낭비" 가 최대. 이를 막기 위해 Redis 장애 전파까지 감수하는 건 비대칭.
- 판단: **기각**.

## 4. 결과 (Consequences)

### 긍정

- **락 점유시간 ms 수준** — TX 지속시간만 잡으므로 Redisson lease 만료 리스크 구조적으로 제거.
- **외부 I/O 가 락 밖** — Tripo rate limit · 업로드 지연 · S3 업로드 지연 모두 락에 영향 없음.
- **Poller 단순화** — stateless 로 운영, Redis 장애에도 독립적 동작. 수평 확장 자유.
- **Reconcile ↔ Worker 자연 협조** — 같은 락 키 공유로 "살아있는 Worker 는 건드리지 말 것" 이 구조적 보장.
- **조건부 UPDATE 원칙** — 잠정적으로 락이 실패해도 `WHERE current_step = 'EXPECTED'` 가 최후 안전망으로 작동.

### 부정

- **조건부 UPDATE 일관 적용 요구** — 모든 상태 전이 쿼리가 `WHERE current_step` 절을 포함해야 함. 누락 시 묵묵히 상태 꼬임. ArchUnit · 리뷰어 검증 필요.
- **heartbeat 구현 부가 비용** — 외부 I/O 루프에 주기적 DB UPDATE 삽입 필요 (30초 주기 추천).
- **affected_rows 분기 로직** — `return affected > 0` 패턴을 모든 상태 전이 호출부에서 처리.
- **External State Verification 의존** — DOWNLOADING 복구 시 S3 HEAD 호출 추가 (비용 미미하지만 로직 복잡도 +).

### 검증

- ArchUnit: `model_generation_workflow` 를 대상으로 한 UPDATE 쿼리 중 `WHERE current_step` 을 포함하지 않은 쿼리 0건.
- 통합 테스트:
  - TX2 직후 크래시 시뮬레이션 → 다른 워커 인수 시 상태 꼬임 없음 (`affected_rows=0` 분기 동작)
  - Reconcile 이 살아있는 워커 작업 중 진입 → 락 획득 실패 또는 heartbeat 재검증 후 skip
  - Poller 2회 동시 실행 → 한 쪽만 `GENERATING→DOWNLOADING` 성공
- 메트릭: `workflow.conditional_update.noop` (affected=0 빈도) 1주 관측 후 race 빈도 수치화.

## 5. 참조

- 설계 문서: [`docs/research/2026-04-23-3d-generation-pipeline-design.md`](../../research/2026-04-23-3d-generation-pipeline-design.md) §6 · §8
- 관련 ADR: ADR-010 (heartbeat_at · current_step 컬럼 소속), ADR-011 (TX2 내 `processed_message` INSERT 원자성)
- 참조 패턴: Optimistic Locking (DB 레벨), Redisson Lock (분산 락), External State Verification (Stripe 패턴)
