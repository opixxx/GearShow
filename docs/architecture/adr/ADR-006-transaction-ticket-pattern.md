# ADR-006: Transaction Ticket 패턴 채택

- **날짜**: 2026-04-15
- **상태**: Accepted
- **영역**: 거래 / 결제
- **관련**: ADR-005 (채팅 프로토콜), ADR-007 (BC 경계)

## 결정

채팅방·쇼케이스 상세·기타 진입점에서 거래를 시작할 때 항상 **`TRANSACTION_TICKET`** 을 발급한다.
티켓이 유일한 거래 진입 계약이며, 거래(`TRANSACTION`)와 결제(`PAYMENT`) 엔티티는 티켓만 참조하고
채팅방·쇼케이스 같은 발급처를 직접 의존하지 않는다.

```
진입점 (채팅방 / 쇼케이스 상세 / ...) → TRANSACTION_TICKET 발급
                                           │
                                           ▼
                                  티켓 소비 (원자적 UPDATE)
                                           │
                                           ▼
                                    TRANSACTION 생성
```

## 배경

설계 초안에서는 `TRANSACTION.chat_room_id`로 채팅방을 직접 FK 참조하는 구조였다 (기존 `schema.md`).
이 구조의 문제:

1. **강결합**: 거래가 채팅방 스키마·생명주기에 종속. 채팅 스키마 변경이 거래에 번짐
2. **확장 불가**: "채팅 외 진입점에서 거래 시작" (마이페이지 직접 요청, 쇼케이스 상세 바로 구매) 불가능
3. **딥링크 조작 공격**: 채팅방 내 결제 링크에 `amount`, `sellerId`를 노출하면 **클라이언트가 값을 바꿀 수 있음**
4. **유효기간·재사용 통제 부재**: 거래 요청이 영원히 유효하거나 여러 번 소비될 수 있음

당근페이 발표 *"송금의 플랫폼화: 중고거래 채팅 벗어나기"* (2025, 유호성)가 동일 문제를 정확히 짚었다:
- **송금 세션 부재**: `daangn://pay/wallet?금액=1000&유저ID=B` 같은 딥링크는 추적·제한·검증 불가
- **채팅방 강결합**: 송금 서버가 채팅방 종류를 직접 알아야 함

해결책이 **티켓 패턴**: 서버가 맥락을 고정해서 발급한 티켓 ID만 클라이언트에 노출하고, 실제 송금은 티켓 소비로 일어남.

## 티켓 3요소

| 요소 | 의미 | 예시 |
|---|---|---|
| **맥락 (Context)** | 왜 거래하는가 | `SHOWCASE_DIRECT` / `SHOWCASE_ESCROW` |
| **대상 (Target)** | 누구에게 무엇을 | sellerId + amount + paymentMethod |
| **정책 (Policy)** | 어떤 조건으로 | expiresAt (1시간), 1회용, 발급자 취소 가능 |

## 상태 전이

```
ISSUED → USED        (소비 성공, 거래 생성)
       → EXPIRED     (유효기간 만료)
       → CANCELLED   (발급자 취소)
```

**원자적 소비 쿼리**:
```sql
UPDATE transaction_ticket
SET ticket_status = 'USED',
    used_at = NOW(),
    used_by_transaction_id = ?
WHERE ticket_id = ?
  AND ticket_status = 'ISSUED'
  AND expires_at > NOW();
-- affected rows == 1 이어야 성공. 아니면 이미 소비됨/만료/취소.
```

이게 **동시성 멱등성 보장의 유일한 지점**. 중복 소비 시도는 affected rows=0으로 실패.

## 대안과 기각 사유

| 대안 | 기각 사유 |
|---|---|
| `TRANSACTION.chat_room_id` FK 유지 | 당근 발표에서 검증된 실패 패턴 반복 |
| 멱등성 키 헤더만 사용 (`Idempotency-Key`) | 클라이언트가 키 관리 부담. 티켓은 서버 발급이라 더 안전 |
| 임시 토큰 (JWT) | 맥락 노출됨. 상태 갱신(사용됨/취소됨) 불가. 서버 DB 저장이 제어 강함 |
| 티켓 없이 매 요청마다 확인 | 매 요청마다 "유효한 거래 요청인지" 서버 체크 필요 → 복잡도 역전 |

## 트레이드오프

### 얻은 것
- 채팅과 거래 간 **단방향 의존** (Chat → Ticket → Transaction)
- 거래 엔티티가 채팅방을 몰라도 됨 → 외부 진입점 확장 무료
- 딥링크에 금액·유저ID 노출 안 됨 (`gearshow://transaction/start?ticket=ABC` 만)
- 유효기간·재사용 제어 자연 내장
- 멱등성 보장 (원자적 UPDATE)

### 포기한 것
- **추가 복잡도**: 엔티티 1개 추가, 3단계 플로우(발급→조회→소비), 만료 배치 필요
- **발표자 회고 그대로 해당**: *"송금 코드가 많이 무겁고 복잡해짐. 이런 추상화가 필요한 시점이 맞는가?"*
  - 현재 GearShow에는 쇼케이스 거래 하나만 있음 — **단일 진입점 상태에서는 티켓 = 오버엔지니어링 우려**
  - 그러나 결국 "외부 진입점 필요" 판명 시 마이그레이션 지옥 예상
  - → **초석만 깔아두는 비용이 낮다**고 판단해 채택

### 미래에 재검토할 시점
- 쇼케이스 거래 외 진입점이 실제로 추가될 때 (경매, 공동구매, 선물 등)
- 6개월 운영 후 티켓 소비율·만료율 통계 기반 재평가
- 티켓 유효기간·재사용 정책 변경 요구 발생 시

## 영향 범위

- `docs/diagram/schema.md` — `TRANSACTION_TICKET` 테이블 신규, `TRANSACTION.chat_room_id` 제거, `TRANSACTION.ticket_id` 추가
- `docs/business/biz-logic.md §7-6, §8` — 거래 흐름 재작성
- `docs/spec/api-spec.md` — `POST/GET /transaction-tickets/*` 엔드포인트 추가 (Phase 5)
- Flutter 앱 딥링크 스킴 `gearshow://transaction/start?ticket=...` 처리 필요

## 구현 순서 (향후 Phase)

- **Phase 5**: `TRANSACTION_TICKET` BC 신설 (Aggregate), 발급·조회·취소 API
- **Phase 6**: `TRANSACTION` BC에서 티켓 소비로 거래 생성 (DIRECT 흐름 완결)
- **Phase 7**: ESCROW 거래 + `PAYMENT` 연동 (Toss/Kakao)

## 참고 자료

- 당근페이 발표 *"송금의 플랫폼화: 중고거래 채팅 벗어나기"* (2025, 유호성)
- `docs/research/2026-04-15-chat-design.md` — 리서치 종합
