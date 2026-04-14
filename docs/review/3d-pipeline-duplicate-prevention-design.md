# 3D 모델 생성 파이프라인 — 중복 처리 방지 설계

> 외부 API 호출당 $0.6 — 어떤 장애 시나리오에서도 이중 과금을 막는 방법

---

## 1. 배경 — 왜 중복 처리가 치명적인가

### 문제 정의

사용자가 축구화 사진 4장(앞/뒤/좌/우)을 업로드하면 외부 AI API(Tripo)가 3D 모델을 생성한다.
이 과정에서 **호출 1건 = $0.6** 이 과금되며, 같은 요청이 2번 처리되면 **$1.2** 가 청구된다.

### Tripo API 특성

| 특성 | 영향 |
|---|---|
| 호출당 $0.6 과금 | 중복 호출 = 직접적 금전 손실 |
| taskId 생성 시점 = 과금 시점 | taskId 못 받아도 과금됐을 수 있음 |
| Task 취소 API 없음 | 한 번 생성된 task 되돌릴 수 없음 |
| Idempotency Key 미지원 | 같은 요청 보내도 매번 새 task 생성 |
| 처리 시간 수분 | 동기 처리 불가 → 비동기 파이프라인 필수 |

> **핵심 제약:** Tripo 는 우리가 통제할 수 없는 외부 API 다. 취소 불가, 멱등성 키 미지원 — 따라서 **"우리 쪽에서 절대 2번 호출하지 않는 것"** 이 유일한 방어다.

---

## 2. 시스템 아키텍처

### 전체 구조 (서버 분리 기준)

```
[API Server]                              [3D Worker Server]

  POST /showcases                           Kafka Consumer
    → Showcase 저장 (DB)                      → 멱등성 확인
    → Showcase3dModel 저장 (REQUESTED)        → S3 이미지 다운로드
    → Outbox 메시지 저장                       → Tripo API 호출 ($0.6)
    → 응답 반환 (201)                          → taskId 저장 (GENERATING)

  OutboxRelayScheduler (1초)                PollGenerationStatusService (3초)
    → Kafka publish                           → Tripo 상태 확인
                                              → 완료 시: GLB → S3 → COMPLETED
         ──── Kafka ────→
                                            RecoverStuckModelsService (1분)
                                              → stuck 모델 감지 → 복구
```

### 정상 흐름 타임라인

```
T=0초     사용자 요청
          DB: showcase + images + showcase_3d_model(REQUESTED) + outbox_message
          응답: 201 Created

T=1초     Outbox Relay → Kafka 발행 + outbox published=true

T=2초     Worker 수신
          멱등성 확인 → PREPARING → S3 다운로드 → Tripo 호출($0.6) → GENERATING + taskId

T=5초     폴링 시작 (3초 주기)
~5분      Tripo 상태 반복 확인

T=30초    Tripo 완료 → GLB 다운로드 → S3 저장 → COMPLETED
~5분
```

### 상태 전이도 (설계 반영 후)

```
REQUESTED (등록 시)
  │
  ├─→ PREPARING (Worker 가 잡음, Tripo 호출 전)
  │     │
  │     ├─→ GENERATING + taskId (Tripo 호출 성공)
  │     │     │
  │     │     ├─→ COMPLETED (폴링 → 성공)
  │     │     └─→ FAILED (폴링 → 실패 / 타임아웃 15분)
  │     │
  │     ├─→ FAILED (Tripo Non-retryable: 크레딧 부족, 이미지 거부 등)
  │     ├─→ UNAVAILABLE (Circuit Breaker OPEN)
  │     └─→ REQUESTED (크래시 → Recovery 자동 재시도)
  │
  └─→ REQUESTED (Outbox 발행 지연 → Recovery 재등록)
```

---

## 3. 깨질 수 있는 지점 — 9가지 시나리오

### #1. Outbox 중복 발행 ✅ 안전

**상황:** Kafka 발행 성공 + published 마킹 전 크래시 → 같은 messageId 로 2번 발행

**결과:** Consumer 멱등성(processed_message)이 같은 키로 2번째를 차단. Tripo 중복 호출 없음.

---

### #2. Tripo 응답 지연 + Recovery 재발행 🚨 가장 위험

**상황:** Worker A 가 Tripo 호출 중(5분+ 블로킹). 상태가 REQUESTED 유지. Recovery 가 "stuck" 판단 → 새 messageId 로 재발행 → Worker B 가 Tripo 또 호출.

```
T=0분   Worker A: tryAcquire(msg-A) → Tripo 호출 시작 (블로킹...)
        showcase_3d_model.status = REQUESTED (아직 안 바꿈!)

T=5분   Recovery: "REQUESTED 5분 초과" → 새 Outbox (messageId=msg-B)
        Worker B: tryAcquire(msg-B) → 다른 키라 통과!
        Worker B: 상태 확인 → REQUESTED → 통과!
        Worker B: Tripo 호출 → $0.6 두 번째 과금!

결과: $0.6 × 2 = $1.2 이중 과금
```

**근본 원인:** Tripo 호출 전에 상태가 REQUESTED 로 남아있어서 Recovery 와 다른 Worker 가 끼어들 수 있었음.

---

### #3. PREPARING 전환 후 크래시 (Tripo 호출 전) ✅ 안전

**상황:** REQUESTED → PREPARING 성공 → 크래시 → Tripo 호출 안 됨

**결과:** taskId=NULL = 과금 안 됨 확정. Recovery 가 자동 재시도(retryCount 적용).

---

### #4. Tripo 호출 성공 + taskId DB 저장 실패 ⚠️ $0.6 1회 손실

**상황:** Tripo 호출 → taskId 받음($0.6) → DB 저장 실패 → taskId 미기록

**결과:** $0.6 1회 손실. orphan 마킹 + Alert → 개발자 수동 복구.

---

### #5. Tripo 비즈니스 에러 — catch 누락 🚨 CRITICAL

**상황:** Tripo 가 400/401/403 에러 반환 → TripoApiException → 기존 catch 에 안 잡힘

**결과:** 모델 상태가 전환 안 되고 방치됨. 사용자에겐 영원히 "생성 중".

---

### #6. 멱등성 레코드 + DLT 봉쇄 🚨 CRITICAL

**상황:** tryAcquire 성공(즉시 커밋) → 비즈니스 실패 → Spring Kafka 재시도 → tryAcquire 차단 → "성공" 간주 → DLT 안 감

```
1차 시도:  tryAcquire("17") 성공 [커밋!] → 비즈니스 실패 → throw

재시도 #1: tryAcquire("17") → 이미 존재 → return
           Spring Kafka: "성공했네" → offset commit → DLT 안 감!

결과: 메시지가 "처리 완료" 로 간주됐지만 실제로는 아무것도 안 됨
      멱등성 레코드가 남아있어서 Recovery 도 차단됨
      → 영원히 처리 안 됨
```

---

### #7. 폴링 중 GLB 다운로드 실패 ✅ 안전

**상황:** Tripo 성공 → GLB 다운로드 실패(CDN 에러, 네트워크 등)

**결과:** 다음 폴링 주기(3초)에 자동 재시도. 조회일 뿐이라 추가 과금 없음.

---

### #8. COMPLETED 전환 시 DB 실패 ✅ 안전

**상황:** GLB S3 저장 성공 → DB UPDATE 실패

**결과:** 다음 폴링 주기에 재시도. S3 는 같은 key 덮어쓰기(멱등).

---

### #9. 서버 분리 후 메시지 스키마 불일치 ⚠️ 서비스 중단

**상황:** API 서버가 메시지에 필드 추가 → Worker 미배포 → 역직렬화 실패 → 3D 생성 전체 중단

**원인:** Producer / Consumer 가 별도 배포 → DTO 불일치 구간 발생.

---

## 4. 설계 결정 — 6가지

### 설계 결정 #1: Tripo 호출 전 상태 선행 전환 (PREPARING)

> 해결 시나리오: #2(Recovery 우회), #3(크래시 후 안전한 재시도)

**Before:**
```
REQUESTED → Tripo 호출 (수분 블로킹) → GENERATING
             │
             │ 이 동안 상태가 REQUESTED
             │ Recovery/Worker 가 끼어들 수 있음
```

**After:**
```
REQUESTED → PREPARING → Tripo 호출 → GENERATING + taskId
               │
               │ 이 동안 상태가 PREPARING
               │ Recovery 가 개입 안 함
```

> **핵심 원칙: "외부 API 호출 전에 의도를 기록하라"** — PREPARING 이 "내가 이제 Tripo 부를 거야" 를 DB 에 먼저 남긴다.

---

### 설계 결정 #2: PREPARING 좀비 자동 재시도 (retryCount)

> 해결 시나리오: #3(크래시 후 사용자 개입 없이 자동 복구)

```
PREPARING + taskId=NULL + 5분 초과:
  → retryCount < 3: REQUESTED 로 되돌림 + Outbox 재등록 (자동 재시도)
  → retryCount >= 3: FAILED + Alert (무한 루프 방지)
```

> **안전 근거:** taskId=NULL 이 "과금 안 됐다" 의 증거. 이 상태에서는 재시도해도 이중 과금 위험이 없다.

---

### 설계 결정 #3: Recovery 대상 명확화

> 해결 시나리오: #2, #3, #4 의 Recovery 동작 분리

| 상태 | taskId | Recovery 행동 | 이유 |
|---|---|---|---|
| REQUESTED + 5분 초과 | NULL | Outbox 재등록 | Worker 가 아직 안 잡음 |
| PREPARING + 5분 초과 | NULL | 자동 재시도 (retryCount) | Tripo 호출 전 크래시, 과금 없음 |
| GENERATING | **있음** | 대상 아님 (Polling 담당) | 정상 흐름 |
| GENERATING | **NULL** | 즉시 FAILED + Alert | 비정상 상태 |

---

### 설계 결정 #4: Tripo 에러 분류 + 재시도 한계

> 해결 시나리오: #5(TripoApiException catch 누락)

**Retryable (일시적 장애 → 자동 재시도):**

| Tripo 에러 | 모델 상태 | 자동 재시도 | Alert |
|---|---|---|---|
| 429 Rate Limit | PREPARING 유지 | O | - |
| 500 Server Error | PREPARING 유지 | O | - |

**Non-retryable (영구 실패 → 즉시 FAILED):**

| Tripo 에러 | 모델 상태 | 자동 재시도 | Alert |
|---|---|---|---|
| 400 Invalid Param | FAILED | X | - |
| 400 Image Empty | FAILED | X | - |
| 403 No Credit | FAILED | X | **필수** |
| 401 Auth Failed | FAILED | X | **필수** |

> **재시도 한계:** retryCount >= 3 이면 원인 불문 FAILED + Alert.

---

### 설계 결정 #5: 멱등성 레코드 release 규칙

> 해결 시나리오: #6(tryAcquire 독립 커밋 → DLT 봉쇄)

```
tryAcquire ──→ PREPARING ──→ S3 다운로드 ──→ [Tripo 호출] ──→ taskId 저장
│                                             │
│←──── 이 구간 실패 = release OK ────────────→│
│              과금 전 (안전)                   │←── 이후 실패 = release 금지 ──→
│                                             │         과금 후 (위험)
```

| 실패 시점 | release | 이유 | 복구 방법 |
|---|---|---|---|
| Tripo 호출 전 | **허용** | 과금 안 됨, 재시도 안전 | Spring Kafka 즉시 재시도 |
| Tripo 호출 후 | **금지** | 이미 과금됨, 재시도하면 이중 과금 | orphan 마킹 + Alert |
| 위 둘 다 실패 | Recovery 가 5분 후 정리 | 최후 안전망 | Recovery release + Outbox 재등록 |

---

### 설계 결정 #6: 메시지 스키마 하위 호환

> 해결 시나리오: #9(서버 분리 후 스키마 불일치)

| 스키마 변경 | 허용 여부 | 이유 |
|---|---|---|
| 필드 추가 | **허용** | Consumer 가 `@JsonIgnoreProperties(ignoreUnknown=true)` 로 무시 |
| 필드 삭제 | **금지** | Consumer 가 기대하는 필드가 null → NPE 또는 로직 오류 |
| 필드 타입 변경 | **금지** | 역직렬화 실패 → 전체 메시지 처리 중단 |

> **운영 규칙:** "필드는 추가만, 삭제/변경은 금지." 삭제/변경이 정말 필요해지면 그때 schemaVersion 도입.

---

## 5. 설계 전후 비교

### 상태 전이 비교

**Before:**
```
REQUESTED → GENERATING → COMPLETED / FAILED
문제: REQUESTED → GENERATING 사이 Tripo 블로킹으로 긴 공백
```

**After:**
```
REQUESTED → PREPARING → GENERATING → COMPLETED / FAILED
효과: PREPARING 이 "누군가 처리 중" 을 명시적으로 표현
```

### 방어 체계 비교

**Before — 5중 레이어:**
```
[1] Transactional Outbox          → 메시지 발행 보장
[2] Consumer 멱등성                → 같은 messageId 차단 (Recovery 우회 가능 ❌)
[3] 도메인 상태 머신                → 상태 전이 불변식
[4] 상태 사전 확인                  → REQUESTED 아니면 스킵 (블로킹 중 무력 ❌)
[5] Orphan 방어                    → Tripo 성공 + DB 실패 시 FAILED
```

**After — 7중 레이어:**
```
[1] Transactional Outbox          → 메시지 발행 보장
[2] Consumer 멱등성 + release 규칙 → 과금 경계 기반 관리 (설계 #5)
[3] 도메인 상태 머신 + PREPARING    → 새 상태 추가 (설계 #1)
[4] 상태 선행 전환                  → Tripo 호출 전 PREPARING (설계 #1)
[5] Tripo 에러 분류                → Retryable / Non-retryable (설계 #4)
[6] 자동 재시도 + 한계              → retryCount 기반 (설계 #2)
[7] 운영 복원력                    → Alert + 수동 복구 경로
```

### 시나리오별 최종 결과

| # | 시나리오 | 이중 과금? | 해결 방법 |
|---|---|---|---|
| 1 | Outbox 중복 발행 | ❌ 없음 | 멱등성이 차단 (기존) |
| 2 | Tripo 응답 지연 + Recovery | ❌ **해결** | PREPARING 선행 전환 (설계 #1) |
| 3 | PREPARING 후 크래시 | ❌ 없음 | 자동 재시도 (설계 #2) |
| 4 | Tripo 성공 + DB 실패 | ⚠️ $0.6 1회 | orphan + Alert (설계 #3) |
| 5 | Tripo 비즈니스 에러 | ❌ 없음 | 에러 분류 (설계 #4) |
| 6 | 멱등성 + DLT 봉쇄 | ❌ 없음 | release 규칙 (설계 #5) |
| 7 | GLB 다운로드 실패 | ❌ 없음 | 폴링 자동 재시도 (기존) |
| 8 | COMPLETED DB 실패 | ❌ 없음 | 폴링 자동 재시도 (기존) |
| 9 | 스키마 불일치 | - | 하위 호환 규칙 (설계 #6) |

---

## 6. 핵심 설계 원칙

### 원칙 1: 외부 API 호출 전에 의도를 기록하라

PREPARING 상태로 "내가 이제 Tripo 부를 거야" 를 DB 에 먼저 남긴다.
중간에 뭐가 깨져도 다른 컴포넌트가 "누군가 처리 중" 을 알 수 있다.

### 원칙 2: taskId 유무가 과금 여부의 증거다

- taskId=NULL: Tripo 과금 안 됨 → 재시도 안전
- taskId 있음: Tripo 과금 됨 → 재시도 위험
- 이 구분이 모든 Recovery 동작의 판단 기준

### 원칙 3: 멱등성 레코드는 과금 경계를 기준으로 관리하라

- 과금 전 실패: release (재시도 허용)
- 과금 후 실패: 유지 (이중 과금 방지)
- "번호표를 언제 반납하느냐" 가 돈을 지키는 열쇠

### 원칙 4: 자동 재시도에는 반드시 한계를 둬라

retryCount >= 3 이면 원인 불문 FAILED + Alert.
Retryable 에러든 크래시든 무한 루프를 구조적으로 차단한다.

### 원칙 5: 서비스 분리 시 메시지는 하위 호환만 허용하라

필드 추가만 허용, 삭제/변경 금지.
Consumer 는 모르는 필드를 무시.
이 규칙 하나가 서버 분리 시 "배포 순서 무관하게 안전" 을 보장한다.
