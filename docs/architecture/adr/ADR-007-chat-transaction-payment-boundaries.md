# ADR-007: 채팅 / 거래 / 결제 Bounded Context 경계

- **날짜**: 2026-04-15
- **상태**: Accepted
- **영역**: 아키텍처 / DDD
- **관련**: ADR-005, ADR-006

## 결정

채팅·거래·결제를 네 개의 독립 Bounded Context로 분리하고, 단방향 의존 관계를 강제한다.

```
┌─────────────────────┐
│ chat                │
│   ChatRoom          │   (1:1, 쇼케이스 단위)
│   ChatMessage       │
│   ChatReadMarker    │
└──────────┬──────────┘
           │ 발급 요청 (TicketIssuePort)
           ▼
┌─────────────────────┐
│ transaction-ticket  │   (독립 Aggregate)
│   TransactionTicket │   맥락·대상·정책
└──────────┬──────────┘
           │ 소비 이벤트 (TicketConsumedEvent)
           ▼
┌─────────────────────┐
│ transaction         │
│   Transaction       │   DIRECT / ESCROW
└──────────┬──────────┘
           │ 결제 요청 (PaymentPort) — ESCROW만
           ▼
┌─────────────────────┐
│ payment             │
│   Payment           │   Toss / Kakao
└─────────────────────┘
```

## 의존 방향 규칙

| From \ To | chat | ticket | transaction | payment |
|:---:|:---:|:---:|:---:|:---:|
| **chat** | — | ✅ Port 호출 | ❌ 금지 | ❌ 금지 |
| **ticket** | ❌ 금지 | — | ❌ 금지 (이벤트 발행만) | ❌ 금지 |
| **transaction** | ❌ 금지 | ✅ 읽기 (참조) | — | ✅ Port 호출 |
| **payment** | ❌ 금지 | ❌ 금지 | ❌ 금지 (이벤트 발행만) | — |

**핵심 원칙**:
- 아래쪽은 위쪽을 모른다 (티켓은 채팅을 모름, 거래는 채팅을 모름, 결제는 거래를 모름)
- 위쪽은 아래쪽을 포트(인터페이스)로 호출한다
- 역방향 통신은 반드시 **이벤트**로 (outbox 경유 Kafka)

## 각 BC의 책임

### chat
**소유**: ChatRoom, ChatMessage, ChatReadMarker, 시스템 메시지 주입 로직, WebSocket/STOMP 어댑터
**책임**:
- 1:1 대화 기능 (수신·발신·읽음·삭제)
- 시스템 메시지 타입 관리 (TICKET_ISSUED, TRANSACTION_STARTED 등)
- ticket BC로부터 이벤트를 구독하여 시스템 메시지 삽입

**하지 않는 것**: 거래·결제 상태 관리, 티켓 발급 로직 자체

### transaction-ticket (신규 BC)
**소유**: TransactionTicket, 발급·조회·취소·소비 로직, 만료 배치 스케줄러
**책임**:
- 거래 요청의 맥락·대상·정책 고정
- 멱등성·동시성 보장 (원자적 소비)
- 티켓 상태 변경 이벤트 발행 (ISSUED, USED, EXPIRED, CANCELLED)

**하지 않는 것**: 실제 거래 생성 (transaction BC가 소비 이벤트 받아서 생성), 채팅방 조회

### transaction
**소유**: Transaction, 거래 상태 머신 (DIRECT, ESCROW), 상태 전이 규칙
**책임**:
- 티켓 소비 결과로 Transaction 생성
- DIRECT 흐름 (즉시 COMPLETED)
- ESCROW 흐름 (PENDING → IN_PROGRESS → COMPLETED)
- 쇼케이스 상태(SOLD) 전이 트리거

**하지 않는 것**: 채팅방·채팅 메시지 직접 참조, 결제 내부 로직

### payment
**소유**: Payment, PG 연동 어댑터(Toss/Kakao), 결제 상태
**책임**:
- 결제 요청·결제 완료·환불 처리
- PG 콜백 수신·멱등성 보장
- 결제 결과 이벤트 발행 (PAID, FAILED, CANCELLED)

**하지 않는 것**: 거래 상태 직접 변경 (transaction BC가 이벤트 받아서 처리)

## 데이터 흐름 예시 — 구매자가 ESCROW 거래 시작

```
① [chat 화면] 구매자가 "안전거래 요청" 버튼
        │
        ▼ HTTP POST /transaction-tickets (via chat.application)
② [transaction-ticket] 서버가 티켓 발급
        - contextType=SHOWCASE_ESCROW
        - amount 확정 (판매자 askingPrice 스냅샷)
        - expiresAt = now + 1h
        │
        ▼ Outbox 이벤트: TicketIssuedEvent
③ [Kafka] chat.ticket-events topic
        │
        ▼ Consumer
④ [chat] TicketIssuedEvent 수신
        → 채팅방에 SYSTEM_TICKET_ISSUED 메시지 자동 삽입 (payload_json: ticket_id)
        → WebSocket으로 양쪽 유저에게 브로드캐스트

⑤ [클라이언트] 판매자가 티켓 수락 (딥링크 진입)
        │
        ▼ HTTP POST /transactions (ticket_id)
⑥ [transaction] 티켓 원자적 소비 (UPDATE ... WHERE status='ISSUED')
        → 성공 시 Transaction 생성 (status=PENDING)
        │
        ▼ Outbox 이벤트: TransactionCreatedEvent
⑦ [Kafka]
        │
        ├─► [chat] SYSTEM_TRANSACTION_STARTED 메시지 삽입
        └─► [payment] 결제 준비 (ESCROW 경우)
```

## 대안과 기각 사유

### 대안 1: 모놀리식 단일 BC로 유지

**기각 사유**: 현재 규모에선 단순하지만 **도메인 규칙이 뒤섞임**. 채팅 규칙 바꾸면 거래 코드까지 건드리게 됨. 2026년 초 3D 파이프라인 리팩토링이 이 패턴의 유지보수 비용을 이미 증명.

### 대안 2: transaction-ticket 을 별도 BC 아닌 transaction 의 내부 서브도메인으로

**기각 사유**: 티켓의 맥락(`contextType`)이 transaction의 세부(method)와 다름. 티켓은 `SHOWCASE_DIRECT|SHOWCASE_ESCROW|(미래: AUCTION|GIFT|...)` 등 진입점 종류를, transaction은 거래 방식(DIRECT|ESCROW)을 표현. 별도 BC가 변경 축을 분리.

### 대안 3: chat 에서 transaction 직접 호출 (ticket 생략)

**기각 사유**: ADR-006 참조. 강결합·조작 공격·멱등성 문제 재발.

## 트레이드오프

### 얻은 것
- 각 BC가 독립적 진화 (채팅 스키마 바꿔도 거래 무영향)
- 테스트 격리 (BC 단위 단위테스트 가능)
- 다른 진입점 확장 자연스러움
- 팀 확장 시 BC별 분담 쉬움

### 포기한 것
- **초기 구현 복잡도**: 4개 BC × port/adapter × 이벤트 = 보일러플레이트 증가
- **이벤트 드리븐 디버깅**: 동기 호출보다 원인 추적 어려움 (Outbox 로그 필수)
- **트랜잭션 경계**: 한 사용자 액션이 여러 BC에 걸칠 때 분산 트랜잭션 고민 필요 (Outbox 패턴으로 해결)

### 미래에 재검토할 시점
- 3개 BC 이상 동시 변경이 반복되면 경계 재정의 필요
- 이벤트 종류가 많아져 신규 개발자가 흐름 파악 못 함 → 이벤트 카탈로그 도입

## 영향 범위

- `backend/src/main/java/com/gearshow/backend/` 에 신규 BC 4개 패키지
  - `chat/`
  - `transactionticket/` (새 BC)
  - `transaction/`
  - `payment/`
- ArchUnit 규칙 보강: BC 간 의존 방향 검증 (ADR-007 기반)
- Outbox 토픽 계약 문서화 (`docs/spec/events/*.md` 향후 신설)

## 참고 자료

- ADR-005 — 채팅 프로토콜 선택
- ADR-006 — Transaction Ticket 패턴
- `docs/business/biz-logic.md §7-9`
- 당근페이 발표 "송금의 플랫폼화" (2025)
