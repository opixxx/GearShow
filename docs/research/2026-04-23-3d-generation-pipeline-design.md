# 3D Model Generation Pipeline — 최종 설계 문서 (v1)

- **작성일**: 2026-04-23
- **상태**: Phase 1 설계 확정
- **후속**: ADR-010/011/012 작성 → EXEC_PLAN 분할

---

## 1. 목표 및 제약

| 항목 | 내용 |
|---|---|
| **목표** | 쇼케이스 이미지 4장 → Tripo API → `.glb` 3D 모델 생성 파이프라인 |
| **핵심 제약** | Tripo 이중 과금 0, 최종 상태 유실 0, 단일 장애로 파이프라인 정지 없음 |
| **Tripo 특성** | 평균 2분 처리, Idempotency-Key 미확인 (확인 필요), Webhook 없음, 결과 URL 60s TTL, 동시 10 슬롯 제한 |
| **스택** | Java 21 / Spring Boot 3 / MySQL 8 / Kafka / 자체호스팅 Redis |

---

## 2. 아키텍처 개요

```
┌──────────────┐
│   Flutter    │
└──────┬───────┘
       │ ① pre-signed PUT × 4           ② POST /showcase (Idempotency-Key)
       ├────────────────────────┐              │
       ▼                        ▼              ▼
┌──────────────┐         ┌─────────────────────────────┐
│  S3 입력      │         │ Backend (API Server)         │
│ (쇼케이스 사진) │         │ - idempotency_key            │
└──────┬───────┘         │ - Showcase / sc_3d_model     │
       │                 │ - model_generation_workflow  │
       │                 │ - outbox                     │
       │                 └──────┬──────────────────────┘
       │                        │ ③ Outbox Relay (1s)
       │                        ▼
       │                 ┌────────────┐
       │                 │   Kafka    │
       │                 │ model-gen  │
       │                 │ retry/dlq  │
       │                 └──────┬─────┘
       │   ⑤ GET × 4            │ ④ consume
       │ ◄──────────────────┐   │
       │                    ▼   ▼
       │               ┌──────────────┐    ⑥ upload+task    ┌─────────┐
       │               │   Worker     │ ──────────────────► │  Tripo  │
       │               │   (JVM)      │ ◄─────── task_id   │   API   │
       │               │              │                     │         │
       │               │   Poller     │ ⑦ GET task          │         │
       │               │              │ ─────────────────►  │         │
       │               │              │ ◄─ url (60s TTL)   │         │
       │               │              │                     │         │
       │               │   Downloader │                     └─────────┘
       │               └──────┬───────┘
       │                      │ ⑧ PUT (.glb)
       ▼                      ▼
┌──────────────────────────────────────────┐
│   S3 출력 (gearshow-models/*.glb)         │
└──────────────────────────────────────────┘

┌──────────────────┐   ┌──────────────────────────────┐
│ Reconcile Batch  │   │   Redis (자체호스팅 + RDB+AOF)  │
│ (stuck 감지)      │   │ - DelayedQueue (적응형 폴링)   │
└──────────────────┘   │ - Semaphore(10) (Tripo 슬롯)   │
                       │ - ZSET (트래픽 피크 대기열)      │
                       │ - Lock (workflow 상태 전이 보호) │
                       └──────────────────────────────┘
```

---

## 3. 데이터 모델

### 3.1 API 경계

```sql
CREATE TABLE idempotency_key (
  `key`           VARCHAR(64) PRIMARY KEY,
  user_id         BIGINT NOT NULL,
  http_status     INT,
  response_body   JSON,
  status          ENUM('IN_PROGRESS','DONE'),
  created_at      TIMESTAMP(6),
  expires_at      TIMESTAMP(6),
  INDEX (expires_at)
);
```

### 3.2 도메인

```sql
CREATE TABLE showcase (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  title           VARCHAR(200),
  description     TEXT,
  image_s3_keys   JSON,             -- 4장 S3 key
  content_hash    CHAR(64),         -- sha256(이미지 원본)
  created_at      TIMESTAMP(6),
  updated_at      TIMESTAMP(6),
  UNIQUE KEY uk_user_content_recent (user_id, content_hash)
  -- 중복 생성 방지 (API 레벨에서 10분 창 검사)
);

CREATE TABLE sc_3d_model (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  showcase_id     BIGINT NOT NULL UNIQUE,
  model_url       VARCHAR(512),     -- s3://gearshow-models/{workflowId}.glb
  format          VARCHAR(16),
  file_size       BIGINT,
  created_at      TIMESTAMP(6),
  updated_at      TIMESTAMP(6)
);
```

### 3.3 프로세스 (오케스트레이션)

```sql
CREATE TABLE model_generation_workflow (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  showcase_id          BIGINT NOT NULL,
  idempotency_key      VARCHAR(64) UNIQUE NOT NULL,
  attempt_no           INT NOT NULL DEFAULT 1,
  current_step         ENUM('REQUESTED','PREPARING','GENERATING',
                            'COMPLETED','FAILED') NOT NULL,
  tripo_task_id        VARCHAR(64),
  tripo_succeeded_at   TIMESTAMP(6) NULL,   -- GENERATING 내부 서브-단계 구분
                                            -- NULL = Tripo 처리 중
                                            -- NOT NULL = S3 미러링 중
  retry_count          INT DEFAULT 0,
  failure_code         VARCHAR(64),
  failure_message      TEXT,
  failure_source       VARCHAR(32),         -- TRIPO_API | S3 | NETWORK | SCHEDULER
  last_polled_at       TIMESTAMP(6),
  heartbeat_at         TIMESTAMP(6),        -- I/O 중 주기 갱신, Reconcile 오판 방지
  started_at           TIMESTAMP(6),
  finished_at          TIMESTAMP(6),
  created_at           TIMESTAMP(6),
  updated_at           TIMESTAMP(6),
  INDEX (current_step, heartbeat_at),
  INDEX (current_step, tripo_succeeded_at),
  INDEX (current_step, last_polled_at),
  INDEX (showcase_id, attempt_no)
);

CREATE TABLE tripo_pending_task (
  workflow_id     BIGINT PRIMARY KEY,
  task_id         VARCHAR(64) NOT NULL,
  created_at      TIMESTAMP(6)
  -- Tripo POST 성공 직후 선저장 → TX2 에서 삭제
  -- 워커 크래시 시 task_id 유실 방지
);

CREATE TABLE outbox (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id        VARCHAR(64) UNIQUE,        -- = hash(idempotency_key)
  aggregate_id    BIGINT,
  event_type      VARCHAR(64),
  payload         JSON,
  published_at    TIMESTAMP(6) NULL,
  INDEX (published_at, id)
);

CREATE TABLE processed_message (
  event_id        VARCHAR(64) PRIMARY KEY,
  consumer        VARCHAR(64),
  processed_at    TIMESTAMP(6)
);
```

### 3.4 Redis 키 공간

| Key | 용도 | TTL |
|---|---|---|
| `workflow:lock:{workflowId}` | 상태 전이 TX 보호 (분산 락) | 10s (watchdog) |
| `tripo:semaphore` | 10 슬롯 rate limit | 영구 |
| `tripo:queue` | ZSET, score=enqueuedAt (피크 시 대기열) | 영구 |
| `poll:delayed-queue` | Redisson DelayedQueue (적응형 폴링) | 영구 |

### 3.5 Kafka 토픽

| 토픽 | 용도 | Key |
|---|---|---|
| `model-generation` | 메인 | `workflowId` |
| `model-generation-retry` | `@RetryableTopic` exp backoff | `workflowId` |
| `model-generation-dlq` | 최종 실패 | `workflowId` |

---

## 4. 3계층 멱등성 전략

| 경계 | 식별자 | 출처 | 보호 대상 |
|---|---|---|---|
| **① API** | `Idempotency-Key` 헤더 | 클라 UUID, localStorage persist | 따닥 / 세션 재진입 / 새로고침 |
| **② 비즈니스 fallback** | `UNIQUE(user_id, content_hash, 10min)` | 서버 sha256 계산 | ①이 실패한 경우 내용 기반 dedup |
| **③ Kafka Consumer** | `processed_message.event_id = hash(idempotency_key)` | ①에서 결정적 파생 | 브로커 재전송 / rebalance |
| **④ Tripo** | `Idempotency-Key` 헤더 (`workflowId`) + `tripo_pending_task` 선저장 | workflow | 이중 과금 |

---

## 5. 상태 머신

```
      ┌─────────────────────────────────────────────┐
      ▼                                             │ RetryTopic
  REQUESTED                                         │ backoff
      │ TX1: Worker 락 안                           │
      ▼                                             │
  PREPARING ────────────────────────────────────────┤
      │ S3 GET + Tripo upload + Tripo POST         │
      │ TX2: Worker 락 안                           │
      ▼                                             │
  GENERATING                                        │
   │  │ tripo_succeeded_at IS NULL  → Tripo 처리 중 │
   │  │ Poller 조건부 UPDATE (락 無)                │
   │  │                                             │
   │  ▼ Tripo SUCCESS 인지                          │
   │  tripo_succeeded_at = NOW()                    │
   │  │ (컬럼만 UPDATE, current_step 유지)          │
   │  │                                             │
   │  ▼ Downloader 실행                            │
   │  Tripo GET url + S3 PUT (락 밖)                │
   │  │ TX_final: Worker 락 안                     │
   │  ▼                                             │
  COMPLETED                                         │
                                                    │
  FAILED ◄──────────────────────────────────────────┘
  (failure_code + failure_source 구조화 저장)
```

**4-state 머신** + `tripo_succeeded_at` 컬럼으로 GENERATING 서브-단계 구분.

---

## 6. 동시성 제어 원칙

### 6.1 락은 "상태 전이 TX" 에만

```
┌────────────────────────────────────────────────────┐
│  락 사용: Worker TX1 / TX2 / TX_final + Reconcile  │
│  이유: 외부 부작용 직전/후 + 다중 인스턴스 경합 대비    │
│                                                    │
│  락 없음: Poller                                    │
│  이유: Tripo GET 멱등 + 조건부 UPDATE 로 충분        │
└────────────────────────────────────────────────────┘
```

### 6.2 락 범위 원칙

> **모든 외부 I/O (S3, Tripo) 는 락 밖. 락은 TX 지속시간 (ms 수준) 만.**

### 6.3 조건부 UPDATE (Optimistic State Check)

모든 상태 전이 TX 에 강제:

```sql
UPDATE model_generation_workflow
   SET current_step = 'NEXT', heartbeat_at = NOW(), updated_at = NOW()
 WHERE id = ?
   AND current_step = 'EXPECTED_PREV'
```

`affected rows = 0` → 현재 상태 재조회 후 분기 (좀비 워커 방어).

### 6.4 Heartbeat

구간 A/B 의 긴 I/O 중 주기 갱신 → Reconcile 이 `heartbeat_at` 기준으로 stuck 판정.

### 6.5 External State Verification

S3 객체 존재 여부 = 진실:

```java
if (s3.headObject("gearshow-models/" + workflowId + ".glb").isPresent()) {
    // 이미 업로드 완료 → 바로 COMPLETED 전이 (재다운로드 스킵)
}
```

### 6.6 Reconcile vs Worker 협조

같은 `workflow:lock:{workflowId}` 공유 → Worker 가 쥐고 있으면 Reconcile 이 자연스럽게 대기/skip.

---

## 7. 시퀀스 — Happy Path

```
[0] Flutter → Backend: POST /showcase/images/upload-urls
    Backend → Flutter: 4개 pre-signed PUT URL

[1] Flutter → S3: PUT × 4

[2] Flutter → Backend: POST /showcase
      Header:  Idempotency-Key: <UUID>
      Body:    { imageS3Keys, contentHash, title, ... }

    Backend TX:
      idempotency_key INSERT (IN_PROGRESS)
      content_hash UNIQUE 체크 (10min 창)
      showcase INSERT
      sc_3d_model INSERT (model_url = NULL)
      model_generation_workflow INSERT (REQUESTED, attempt_no=1)
      outbox INSERT (event_id = hash(idempotency_key))
      idempotency_key UPDATE (DONE, response)
    COMMIT

    Backend → Flutter: 200 OK { showcaseId, workflowId }

[3] OutboxRelay (1s poll):
    SELECT WHERE published_at IS NULL LIMIT 100
    Kafka 발행 (topic=model-generation, key=workflowId)
    UPDATE outbox SET published_at = NOW()

[4] Worker Consumer:
    redisson.lock("workflow:lock:" + workflowId) [10s]
    BEGIN TX1
      SELECT FOR UPDATE workflow WHERE id=?
      UPDATE current_step='PREPARING', heartbeat_at=NOW()
        WHERE id=? AND current_step='REQUESTED'
    COMMIT
    unlock

[5] [락 밖]
    S3 GET × 4
    (주기적 heartbeat UPDATE)
    Tripo /v2/upload/sts × 4 → file_token × 4
    tripo:queue ZSET 대기 (피크 시) + 세마포어 획득
    POST /v2/task
       Header: Idempotency-Key: <workflowId>
       Body:   { type: 'multiview_to_model', file_tokens }
    → task_id 획득
    INSERT tripo_pending_task(workflow_id, task_id)   -- 선저장

[6] Worker:
    redisson.lock("workflow:lock:" + workflowId) [10s]
    BEGIN TX2
      SELECT FOR UPDATE workflow WHERE id=?
      UPDATE current_step='GENERATING', tripo_task_id=?, heartbeat_at=NOW()
        WHERE id=? AND current_step='PREPARING'
      affected_rows == 0 → 좀비 워커, rollback 후 task_id 폐기
      INSERT processed_message(event_id)
      DELETE tripo_pending_task(workflow_id)
    COMMIT
    unlock
    ack Kafka
    DelayedQueue.offer(workflowId, delay=30s)

[7] Poller (DelayedQueue consumer, 락 없음):
    workflow = SELECT ...
    if current_step != GENERATING → skip
    if tripo_succeeded_at IS NOT NULL → skip (Downloader 담당)

    tripo = tripoClient.getTask(workflow.tripoTaskId)   // 부작용 無

    case IN_PROGRESS:
        UPDATE last_polled_at=NOW()
          WHERE id=? AND current_step='GENERATING'
                    AND tripo_succeeded_at IS NULL
        if affected > 0 → delayedQueue.offer(delay=30s)

    case SUCCESS:
        UPDATE tripo_succeeded_at=NOW(), last_polled_at=NOW()
          WHERE id=? AND current_step='GENERATING'
                    AND tripo_succeeded_at IS NULL
        if affected > 0 → downloadExecutor.submit(() -> download(workflowId))

    case FAILED/REJECTED/INSUFFICIENT_CREDIT:
        UPDATE current_step='FAILED',
               failure_code=?, failure_message=?, failure_source='TRIPO_API',
               finished_at=NOW()
          WHERE id=? AND current_step='GENERATING'
        ApplicationEvent: ModelGenerationFailed

[8] Downloader (별도 thread pool):
    [락 밖]
    - Tripo GET task/{taskId} → 새 download_url (60s TTL)
    - 스트림 다운로드 → S3 PUT "gearshow-models/{workflowId}.glb" (결정적 key)
    - 주기적 heartbeat UPDATE

    redisson.lock("workflow:lock:" + workflowId) [10s]
    BEGIN TX_final
      SELECT FOR UPDATE workflow WHERE id=?
      UPDATE current_step='COMPLETED', finished_at=NOW()
        WHERE id=? AND current_step='GENERATING'
                  AND tripo_succeeded_at IS NOT NULL
      affected_rows == 0 → 재진입, s3.headObject 로 검증
      UPDATE sc_3d_model SET model_url, format, file_size
    COMMIT
    unlock
    ApplicationEvent: ModelGenerationCompleted(showcaseId)

[9] Notification Consumer → FCM 푸시
```

---

## 8. 시퀀스 — 실패 / 복구

### 8.1 Transient 실패 (Tripo 429, S3 일시 장애)

```
Worker catch RetryableException:
  조건부 UPDATE: PREPARING → REQUESTED (재시작 가능)
  publish to model-generation-retry (retry_count++)

Retry Topic (@RetryableTopic):
  exponential backoff: 1s → 5s → 30s → 5min
  retry_count > 3 → DLQ + 운영 알림
```

### 8.2 Terminal 실패

```
Worker:
  조건부 UPDATE: → FAILED, failure_code, failure_message, failure_source, finished_at
  ApplicationEvent: ModelGenerationFailed
  → FCM + 사용자 UI "재시도" 버튼 노출
```

### 8.3 사용자 재시도

```
POST /showcase/{id}/retry-3d
  Backend TX:
    INSERT model_generation_workflow (
      showcase_id = 기존,
      attempt_no = MAX+1,
      idempotency_key = 새 UUID,
      current_step = REQUESTED
    )
    INSERT outbox
  → 단계 [3] 부터 재개
  → 이전 attempt 의 failure_* 는 그대로 보존 (디버깅용)
```

### 8.4 Reconcile 배치 (매 1분)

```
for step in {PREPARING, GENERATING, COMPLETED_PENDING}:
    stuck_list = workflowRepo.findStuck(step)
    for each:
        tryLockAndRecover(w)

void tryLockAndRecover(w):
    lock = redisson.getLock("workflow:lock:" + w.id)
    if (!lock.tryLock(2s, 10s)) return          -- 워커가 작업 중
    try:
        fresh = SELECT FOR UPDATE
        if (heartbeat 유효) return               -- 오판 방어

        switch (fresh.currentStep, fresh.tripoSucceededAt):
            (PREPARING, _):
                - tripo_pending_task 조회로 task_id 복구 (있으면)
                - 없으면 Tripo list (metadata 필터) 로 기존 task 조회
                - 없으면 재 POST (Idempotency-Key = workflowId)
                - TX2 로 진행
            (GENERATING, NULL):       -- Tripo 처리 중 stuck
                - Tripo GET → 현재 상태에 따라 분기
                - 응답 없음/404 → failure_code=POLLING_LOST
            (GENERATING, NOT NULL):   -- S3 업로드 중 stuck
                - s3.headObject → 있으면 TX_final 로 COMPLETED 전이
                - 없으면 재다운로드
    finally:
        lock.unlock()
```

**stuck 임계값 (초기, 운영 1주 후 튜닝)**

| 조건 | 임계 |
|---|---|
| REQUESTED + (now - created_at > 30s) | Outbox Relay 점검 |
| PREPARING + (heartbeat 갭 > 1min) | Worker 사망 의심 |
| GENERATING + tripo_succeeded_at IS NULL + (last_polled_at 갭 > 8min) | Tripo 처리 stuck |
| GENERATING + tripo_succeeded_at IS NOT NULL + (heartbeat 갭 > 5min) | S3 미러링 stuck |

---

## 9. failure_code 분류표

| code | source | 재시도 | DLQ | 사용자 액션 |
|---|---|---|---|---|
| `TRIPO_429_RATE_LIMIT` | TRIPO_API | ✅ | ❌ | 자동 재시도 |
| `TRIPO_TIMEOUT_GENERATING_15M` | SCHEDULER | ❌ | ✅ | 운영 알림, 수동 재시도 |
| `TRIPO_REJECTED_INVALID_IMAGE` | TRIPO_API | ❌ | ❌ | 이미지 교체 UI |
| `INSUFFICIENT_CREDIT` | TRIPO_API | ❌ | ✅ | 운영 알림 (크레딧 충전) |
| `S3_UPLOAD_FAILED` | S3 | ✅ | ❌ | 자동 재시도 |
| `POLLING_LOST` | SCHEDULER | ❌ | ✅ | 수동 조사 |
| `UNKNOWN_TRIPO_RESPONSE` | TRIPO_API | ❌ | ✅ | 수동 조사 |

---

## 10. 관찰 지표 (Phase 1 기본)

| 메트릭 | 소스 | 용도 |
|---|---|---|
| `workflow.step.duration{step}` p50/p95/p99 | started_at→finished_at | stuck 임계 튜닝 |
| `workflow.s3_mirror.duration` p95 | tripo_succeeded_at→finished_at | S3 단계 별도 추적 |
| `workflow.failure.count{code}` | failure_code 집계 | 분류별 추이 |
| `tripo.charge.count` | Tripo POST 성공 | 과금 실증 |
| `reconcile.recovered.count{step}` | Reconcile 배치 | 장애 빈도 |
| `dlq.size` | Kafka | 미해결 사고 |

---

## 11. 구현 Phase 분할

| Phase | 내용 | 의존 | 난이도 |
|---|---|---|---|
| **P0-ADR** | ADR-010/011/012 작성 및 리뷰 | — | 낮음 |
| **P1-A** | DB 스키마 마이그레이션 | P0 | 중 |
| **P1-B** | API: `/showcase/images/upload-urls`, `/showcase` + Idempotency-Key | P1-A | 중 |
| **P1-C** | Outbox Relay (기존 재사용) + Kafka 토픽 구성 | P1-B | 낮음 |
| **P1-D** | Worker: TX1/TX2 + Tripo upload/task + pending_task 선저장 | P1-C | 높음 |
| **P1-E** | Poller + DelayedQueue + rate limit 세마포어 (락 無) | P1-D | 중 |
| **P1-F** | Downloader + S3 mirror + TX_final + 도메인 UPDATE | P1-E | 중 |
| **P1-G** | Reconcile 배치 + Retry Topic + DLQ | P1-F | 중 |
| **P1-H** | 관찰 지표 + 대시보드 + 알람 | P1-G | 낮음 |
| **P1-I** | E2E 테스트 (크래시 시뮬레이션, 중복 요청, 재시도) | P1-H | 중 |

---

## 12. 구현 전 확정 필요 결정

| # | 결정 | 영향 | 방법 |
|---|---|---|---|
| 1 | Tripo API `Idempotency-Key` 헤더 지원 여부 | 미지원 시 A-2 이중과금 리스크 수용 or 대안 필요 | Tripo support 문의 |
| 2 | Tripo task list API + metadata 필터 지원 여부 | 1번 미지원 시 fallback 수단 | API 문서 재확인 / 테스트 |
| 3 | Tripo `GET /v2/task` 가 매번 새 download URL 발급하는지 | S3 미러링 복구 경로 결정 | 실측 테스트 |
| 4 | Redis 인스턴스 배치 (동일 VPC? 별도?) | 네트워크 레이턴시 | 인프라 결정 |

---

## 13. ADR 계획

| ADR | 제목 | 핵심 |
|---|---|---|
| **ADR-010** | 테이블 분리: sc_3d_model ⊕ model_generation_workflow | 도메인과 프로세스 분리, 재시도 이력 보존 |
| **ADR-011** | 3계층 멱등성 전략 | API key + content_hash + processed_message + Tripo header |
| **ADR-012** | 선택적 분산 락 + 조건부 UPDATE 기반 동시성 제어 | 상태 전이 TX 에만 락, Poller 는 락 없음 |

---

## 14. 변경 이력

| 날짜 | 내용 |
|---|---|
| 2026-04-23 | v1 초안 — 4-state 머신 (`tripo_succeeded_at` 컬럼) + 폴링 락 제거 방향 확정 |
