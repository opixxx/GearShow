# ADR-011: 3D 파이프라인 — 다층 멱등성 전략

- **상태**: Accepted
- **버전**: v1.1 (2026-04-28 §④ 정정 — `## 6. 변경 이력` 참조)
- **결정일**: 2026-04-23
- **관련 PR**: docs/adr-010-011-012-3d-pipeline
- **결정 주체**: GearShow Backend
- **관련 문서**: [3D 생성 파이프라인 설계 v1.1](../../research/2026-04-23-3d-generation-pipeline-design.md) · [Tripo API 레퍼런스](../../research/2026-04-16-tripo-api-reference.md)
- **관련 ADR**: [ADR-010](./ADR-010-3d-pipeline-table-split.md), [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md)

---

## 1. 배경 (Context)

3D 모델 생성은 **Tripo API 외부 과금 호출** 이 포함되는 파이프라인이다. 이 경로는 단일 지점의 멱등성 방어로는 불충분하다. 서로 다른 레이어에서 다른 원인의 중복이 발생한다:

| 원인 | 발생 경로 | 기존 단일 Consumer 멱등성으로 차단되나? |
|---|---|---|
| 사용자 따닥 (더블 클릭) | 같은 세션에서 `POST /showcase` 2회 | ❌ 서버가 서로 다른 `eventId` 2개를 Outbox 에 넣음 |
| 새로고침 후 재제출 | 다른 세션에서 같은 의도로 재요청 | ❌ 위와 동일 |
| Kafka 브로커 재전송 | 네트워크 흔들림, 소비자 rebalance | ✅ `processed_message` 로 차단 가능 |
| 워커 크래시 후 복구 | Reconcile 이 재처리 시 중복 Tripo POST | ❌ `eventId` 동일해도 Tripo 쪽에서 새 task 생성 가능 |

Kafka 단 멱등성만 있으면 **사용자 유입 중복은 뚫리고** 이중 과금 위험에 그대로 노출된다. 리뷰어가 지적한 핵심 문장:

> "이 멱등성 테이블은 카프카 인프라가 같은 메시지를 두 번 보낼 때만 막아줍니다. 사용자에서부터 시작된 따닥 문제에서 `outbox` 에 서로 다른 eventId 2개를 넣었다고 해봅시다 — 순차적으로 락 잡으면서 이중 과금 되지 않나요? **비즈니스 차원의 식별자가 필요** 해 보입니다."

또한 Tripo API 레퍼런스 조사 결과:

- Tripo 는 `Idempotency-Key` 헤더를 **지원하지 않음** (2026-04 기준 문서 명시 없음).
- Tripo `POST /v2/task/{taskId}/cancel` **엔드포인트도 미지원** (2026-04-28 재확인). 발행된 task 의 사후 취소 경로 없음.
- Tripo 과금은 task 가 `success` 로 완료된 시점에만 `consumed_credit` 차감 — **POST 자체로는 과금되지 않음** (tripo-api-reference §3.2).
- Webhook 미지원 — 능동 알림 수단 없음.

→ Tripo 측 능동 차단 수단(헤더/cancel) 이 모두 없으므로, **중복/오발행 task 발생 자체를 우리 쪽에서 최소화** 하고 잔존 손실은 운영 모니터링으로 인지하는 전략을 택한다.

## 2. 결정 (Decision)

3D 파이프라인에 **4계층 멱등성 방어** 를 채택한다. 각 계층은 서로 다른 원인의 중복을 담당하고, 하나가 뚫려도 다음이 받아낸다.

```
┌── ① API 경계 ──┬── ② 비즈니스 fallback ──┬── ③ Kafka Consumer ──┬── ④ Tripo 손실 수용
│                │                          │                       │
│  Idempotency   │  UNIQUE(user_id,         │  processed_message    │  tripo_pending_task
│  -Key 헤더      │    content_hash, 10min)  │    .event_id          │  선저장
│  (클라 UUID    │                          │    = hash(idemp_key)  │  + Reconcile PREPARING
│   persist)     │                          │                       │    복구 (cancel 불가)
└────────────────┴──────────────────────────┴───────────────────────┴─────────────────────
    따닥·재제출        ①이 뚫려도 내용으로           브로커 재전송·           stranded task 빈도
                      막는 최후 그물 (같은           rebalance 중복          최소화 (cancel/헤더
                      이미지 업로드)                                         모두 미지원)
```

### 계층별 요약

#### ① API — `Idempotency-Key` 헤더
- 클라이언트가 UUID 생성 후 **localStorage 에 persist** → 새로고침/탭 닫음 후에도 재사용.
- 서버 `idempotency_key` 테이블: `IN_PROGRESS` (첫 요청 처리 중) → `DONE` (응답 캐싱).
- 같은 key 재도달 시: `DONE` 면 캐싱 응답 반환, `IN_PROGRESS` 면 409 Conflict.

#### ② 비즈니스 fallback — `content_hash`
- ① 이 실패(클라가 key 를 persist 못한 경우 등)했을 때의 최후 그물.
- `sha256(imageBytes + userId)` 기준 `UNIQUE (user_id, content_hash)` 제약 (생성 10분 이내).
- 같은 이미지를 짧은 창 안에 재업로드 → 기존 리소스 반환.

#### ③ Kafka Consumer — `processed_message`
- `event_id = hash(idempotency_key)` **결정적 파생** → ① 이 차단하면 ③ 은 자연스럽게 무의미해짐(`event_id` 동일 → 즉시 차단).
- 브로커 재전송 · rebalance 로 이미 처리된 메시지가 재배달되는 상황만 담당.
- **완료 시점에 INSERT** (Tripo POST 후 TX2 내에서 처리됨과 같은 트랜잭션). 시작 시점 INSERT 는 크래시 시 복구 경로를 막아버리므로 금지.

#### ④ Tripo — 손실 수용 + 운영 모니터링

Tripo 는 `Idempotency-Key` 헤더와 `cancel` 엔드포인트 모두 미지원 (2026-04-28 재확인). 따라서 ④ 계층은 "중복/오발행 task 의 사후 정리" 가 아니라 **"발생 빈도 최소화 + 잔존 손실 수용"** 으로 정의된다.

- **선저장**: POST 성공 직후 `tripo_pending_task(workflow_id, task_id)` 한 행 INSERT → 워커가 TX2 전 크래시해도 task_id 유실 방지.
- **PREPARING 복구**: Reconcile 이 PREPARING stuck 감지 시 `tripo_pending_task` 에서 task_id 회수 → `markGenerating` 으로 정상 진행 (재 POST 회피).
- **잔존 손실**: 선저장 자체가 실패하거나 (`tripo_pending_task` INSERT `DataAccessException`) Tripo POST 직후 워커가 죽으면 task_id 가 유실된 stranded task 가 1개 발생한다. **cancel 불가** 로 인해 Tripo 백그라운드에서 task 가 완료되며 **1회분 크레딧이 영구 손실**된다. 운영 측 일일 balance 모니터링 + `failure_code=TX2_DB_FAILED` 카운터로 사후 인지한다.

### 양보 불가 규칙

- 클라이언트 `Idempotency-Key` 는 **localStorage 에 persist**, 응답 확인 전까지 삭제 금지. 이유: 새로고침 · 앱 재시작 후에도 같은 key 로 재시도.
- `event_id = hash(idempotency_key)` **결정적 파생**. 서버가 새 UUID 생성 금지. 이유: ①③ 체인 연결.
- `processed_message` INSERT 는 **완료 시점** (TX2 내). 시작 시점 INSERT 금지. 이유: 크래시 복구 경로 차단.
- ④ Tripo 에 `Idempotency-Key` 헤더 붙이지 않음. 지원 안 되는데 붙이면 오도. Trace ID 저장으로 사후 추적만.
- ④ Tripo `cancel` 엔드포인트 호출 금지. 미지원이라 호출해봐야 의미 없고 코드/문서에 "정리 가능" 오해를 만든다. 잔존 stranded task 는 운영 측 balance 모니터링으로 인지한다.
- `content_hash` 창은 **10분 고정**. 이유: 정당한 재업로드(의도적 재시도)를 10분 뒤엔 허용.

## 3. 고려한 대안 (Alternatives)

### A. Kafka Consumer 멱등성(`processed_message`) 단독

- 장점: 구현 최소.
- 단점: 사용자 따닥 · 새로고침 재제출로 **서로 다른 eventId** 가 2개 생성되면 차단 불가. 리뷰어 지적의 핵심 결함.
- 판단: **기각**.

### B. API `Idempotency-Key` 만

- 장점: 따닥 · 재제출 차단.
- 단점: 브로커 재전송 · rebalance 로 인한 중복 Consumer 호출에 취약.
- 판단: **기각**. ③ 이 없으면 "API 멱등성 + Kafka 중복" 조합 상황에서 Tripo 이중 호출.

### C. Tripo `Idempotency-Key` 헤더 또는 `cancel` 엔드포인트 의존

- 장점: 해당 없음 — 헤더/cancel 모두 Tripo 미지원이라 평가 전제 자체가 부재.
- 단점: **두 경로 모두 Tripo 미지원** (2026-04-28 재확인). 헤더로 원천 차단도, 사후 cancel 도 불가.
- 판단: **기각** (우리 통제 밖). ④ 는 발생 빈도 최소화 + 잔존 손실 수용으로 운영.

### D. `content_hash` 단독 (API Key 없이)

- 장점: 서버 측 결정적 dedup.
- 단점: 의도적 재업로드(10분 창 밖)와 중복 요청을 구분 못함. "이미지가 살짝 다른 재제출" (EXIF 차이 등) 차단 불가.
- 판단: **기각**. 클라 주도 key + 서버 fallback 조합이 안전.

### E. Event Sourcing 기반 — "같은 커맨드 ID 는 한 번만 처리"

- 장점: 감사 로그 + 멱등성 동시 달성.
- 단점: Phase 1 범위를 훨씬 넘어섬 (ADR-010 §3-C 참조).
- 판단: **Phase 2 이후**.

## 4. 결과 (Consequences)

### 긍정

- **중복 호출 빈도 최소화** — ①②③으로 사용자/브로커 유입 중복은 원천 차단. ④ 는 stranded task 발생 빈도를 줄이고 (선저장 + PREPARING 복구) 잔존 손실은 수용한다.
- **사용자 유입 중복 원천 차단** — 따닥 · 새로고침 · 앱 재시작 시나리오 모두 ① 또는 ② 에서 흡수.
- **Tripo 측 미지원 환경에서도 강건** — Idempotency-Key 헤더와 cancel 엔드포인트 모두에 의존하지 않음.
- **진단 용이성** — `tripo_trace_id` (X-Tripo-Trace-ID) 저장으로 Tripo 측 장애 문의 지원.

### 부정

- **구현 복잡도** — `idempotency_key` 테이블 + `content_hash` 인덱스 + `tripo_pending_task` + Reconcile PREPARING 복구, 4곳 모두 정합성 유지 필요.
- **클라이언트 책임 증가** — Flutter 가 `Idempotency-Key` 생성 · persist · 삭제 타이밍을 정확히 지켜야 함.
- **잔존 손실 (자동 회수 불가)** — Tripo POST 성공 직후 `tripo_pending_task` INSERT 또는 TX2 가 실패하면 stranded task 1개 발생. cancel 불가로 **1회분 크레딧 영구 손실**. 발생 빈도는 "POST 성공 × 직후 DB 단발성 장애" 라 낮지만, 발생 시 자동 회수 경로가 없으므로 운영 측 일일 balance 모니터링 + ALERT 로그 + `failure_code=TX2_DB_FAILED` 메트릭으로 사후 인지한다.
- **`content_hash` 계산 비용** — 업로드 이미지 4장의 sha256 계산 (~수 MB × 4). 서버 CPU 부하 측정 후 필요 시 클라 계산 이관.

### 검증

- ArchUnit: `application` 계층에서 클라 Idempotency-Key 검증 로직이 API 경계 외에서 호출되는지 확인 (API 경계 한정).
- 통합 테스트: 따닥 시나리오 (같은 key 로 2회 호출 → 1개 workflow 생성), 새로고침 시나리오 (다른 key, 같은 content_hash 10분 창 내 → 1개), brocker 재전송 시나리오 (같은 event_id 2회 → 1회 처리).
- 과금 추적: `failure_code=TX2_DB_FAILED` 카운터 + `tripo.charge.count` 메트릭 + 일일 Tripo balance 모니터링으로 stranded task 발생 빈도를 1주 관측.

## 5. 참조

- 설계 문서: [`docs/research/2026-04-23-3d-generation-pipeline-design.md`](../../research/2026-04-23-3d-generation-pipeline-design.md) §4 · §8.4
- Tripo 과금 규칙: [`docs/research/2026-04-16-tripo-api-reference.md`](../../research/2026-04-16-tripo-api-reference.md) §3.2
- 관련 ADR: ADR-010 (`tripo_pending_task` 테이블 소속), ADR-012 (조건부 UPDATE 로 TX2 내 `processed_message` INSERT 원자성 확보)
- 참조 패턴: Stripe Idempotency-Key (API 경계), Transactional Outbox (Kafka 경계 결정적 event_id)

## 6. 변경 이력

| 날짜 | 버전 | 내용 |
|---|---|---|
| 2026-04-23 | v1.0 | 초안 — 4계층 멱등성 (API key + content_hash + processed_message + Tripo 사후 cancel) |
| 2026-04-28 | v1.1 | §④ "사후 cancel" 가정 정정 — Tripo `cancel` 엔드포인트 미지원 재확인. ④ 를 "손실 수용 + 운영 모니터링" 으로 재정의 (선저장 + PREPARING 복구 유지, cancel 의사코드/배치 삭제). ①②③ 결정은 불변. §1·§2 다이어그램·§3 Alt C·§4 결과·§4 검증·§2 양보 불가 규칙 (cancel 호출 금지 1줄 추가) 동시 정정. |
