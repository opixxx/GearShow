# Chat + Transaction Ticket 설계 리서치

- **날짜**: 2026-04-15
- **대상 기능**: 채팅 MVP + 거래 티켓 도입 (biz-logic.md §7, §8 / api-spec.md §8, §9)
- **관련 ADR**: [ADR-005](../architecture/adr/ADR-005-chat-protocol.md), [ADR-006](../architecture/adr/ADR-006-transaction-ticket-pattern.md), [ADR-007](../architecture/adr/ADR-007-chat-transaction-payment-boundaries.md)
- **하네스 트레젝토리 첫 리서치 문서** — `/review-gap-analysis` 월 스케줄러가 이 문서와 실제 구현 간 갭을 추적하는 기준점이 된다.

---

## 1. 왜 리서치가 필요했나

채팅은 **프로토콜·브로커·저장소·읽음 처리·오프라인 푸시** 5개 축에서 각각 선택이 필요한 대형 기능.
GearShow는 마켓플레이스 특성상 **채팅에서 거래가 시작**되므로 거래·결제와의 경계 설계도 함께 결정해야 했다.

초기 질문 셋:
1. 실시간 프로토콜을 WebSocket으로 갈 것인가, SSE/폴링도 고려할 것인가?
2. 다중 서버 메시지 전파를 Redis Pub/Sub 도입 vs 기존 Kafka 활용?
3. 메시지 저장을 MySQL 유지 vs NoSQL 전환?
4. 읽음 처리를 메시지별 플래그 vs per-user 마커?
5. 채팅방에서 거래를 어떻게 시작하며 거래/결제와는 어떻게 결합/분리할 것인가?

---

## 2. 수집 소스

### 한국 기술 블로그 / 컨퍼런스

| 출처 | 핵심 통찰 |
|---|---|
| [당근마켓 2200만 채팅 시스템 (AWS Summit Korea 2022, 변규현)](https://byline.network/2022/05/0512-2/) | Rails → Go MSA, PostgreSQL → DynamoDB, FCM과 독립된 이벤트 수신 경로, 채팅팀 분리 |
| [**당근페이 송금의 플랫폼화 (2025, 유호성)**](./pdf-summary-daangn-pay-2025.md) | **채팅방 강결합 제거 → 송금 티켓 패턴**. 본 설계의 핵심 근거 |
| [LINE LIVE 채팅 아키텍처](https://engineering.linecorp.com/ko/blog/the-architecture-behind-chatting-on-line-live) | Actor 모델 (ChatSupervisor/ChatRoomActor/UserActor), Redis Pub/Sub |
| [채팅 아키텍처 v1 → v2 (velog/gkdud583)](https://velog.io/@gkdud583/%EC%B1%84%ED%8C%85-%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98-%EC%84%A4%EA%B3%84%ED%95%98%EA%B8%B0-v2) | Redis → MongoDB 전환 회고 — Redis Pub/Sub 휘발성 한계 |
| [Stomp + Kafka 채팅 구현](https://velog.io/@ch4570/Stomp-Kafka%EB%A5%BC-%EC%9D%B4%EC%9A%A9%ED%95%9C-%EC%B1%84%ED%8C%85-%EA%B8%B0%EB%8A%A5-%EA%B0%9C%EB%B0%9C%ED%95%98%EA%B8%B0-with-Spring-Boot-1-Kafka%EC%99%80-Stomp%EB%8A%94-%EB%AC%B4%EC%97%87%EC%9D%BC%EA%B9%8C) | Spring + STOMP + Kafka 기본 구현 패턴 |
| [Redis Pub/Sub → Kafka 전환](https://codesche.oopy.io/289de3f7-e3a8-80c2-8fd7-e14d938939fc) | 실서비스 전환 사례 — 안정성 이유 |
| [Tmax 읽음 처리 / Unread Count](https://tmaxcore.ai/Tech/?bmode=view&idx=92126381) | `lastReadMessageId` 패턴 효율성 |
| [실시간 채팅 읽음 처리 성능 개선](https://velog.io/@ahhpc2012/%EC%8B%A4%EC%8B%9C%EA%B0%84-%EC%B1%84%ED%8C%85-%EC%9D%BD%EC%9D%8C-%EC%B2%98%EB%A6%AC-%EC%84%B1%EB%8A%A5-%EA%B0%9C%EC%84%A0) | MySQL row lock으로 unread 동시성 처리 |

### 해외 / 시스템 설계

| 출처 | 핵심 통찰 |
|---|---|
| [Slack Architecture](https://systemdesign.one/slack-architecture/) | WebSocket + Vitess (MySQL 샤딩) + consistent hashing + dead letter queue |
| [Discord → ScyllaDB 전환](https://medium.com/double-pointer/system-design-interview-facebook-messenger-whatsapp-slack-discord-or-a-similar-applications-47ecbf2f723d) | trillion 메시지 스케일에서의 DB 교체. GearShow 규모엔 과잉 |
| [WebSocket vs SSE vs Long Polling](https://blog.openreplay.com/websockets-sse-long-polling/) | 채팅은 양방향 → WebSocket이 자연 선택 |
| [WebSockets at Scale](https://websocket.org/guides/websockets-at-scale/) | sticky session → 분산 세션 레지스트리 단계적 진화 |

---

## 3. 당근페이 발표에서 얻은 핵심 교훈 (설계 결정의 뿌리)

발표 *"당근페이 송금의 플랫폼화: 중고거래 채팅 벗어나기"* (유호성, 2025)가 GearShow 설계에 직접 영향을 준 4가지:

### 교훈 1 — 티켓 패턴으로 채팅↔거래 경계를 박아라
당근은 초기 송금이 채팅방 정보에 직접 의존 → 다른 진입점(알바·비즈·모임) 확장 불가 → **티켓 발급/조회/사용** 3단계로 추상화.
발급 시 서버가 맥락을 고정하므로 클라이언트 조작 불가.
GearShow도 똑같이 `TRANSACTION_TICKET` 신설.

### 교훈 2 — 강결합은 당장은 편하지만 곧 폭발한다
당근은 "송금 API가 채팅방 종류를 서버 내부 통신으로 파악"하는 중간 단계를 거쳤는데, 이것도 확장성 부족 판정.
GearShow에서 `TRANSACTION.chat_room_id` FK는 같은 함정. **지금 빼는 게 정답**.

### 교훈 3 — 딥링크에 민감 정보를 노출하지 말라
`daangn://pay/wallet?채팅방ID=A&금액=1000&유저ID=B` 는 금액·유저ID 조작 가능, 추적·유효기간도 없음.
티켓 ID만 노출 (`gearshow://transaction/start?ticket=ABC`) 하면 서버 제어 가능.

### 교훈 4 — 과잉 추상화 리스크 인정
발표자 회고: *"송금 코드가 많이 무겁고 복잡해짐. 이런 추상화가 필요한 시점이 맞는가?"*
GearShow는 현재 쇼케이스 진입점 하나지만, 미래 확장 가능성 + 마이그레이션 비용 비교 후 **초석만 깔아두기로** 판단.

---

## 4. 5가지 핵심 의사결정 요약

| 축 | 결정 | 근거 |
|---|---|---|
| **프로토콜** | WebSocket + STOMP + SockJS fallback | Spring 표준, 양방향, 라우팅 내장 |
| **서버 간 브로드캐스트** | Kafka (partition key=roomId) | Redis Pub/Sub 휘발성 회피, 이미 보유, 오프라인 유저 offset 처리 |
| **메시지 저장** | MySQL 유지 | 헥사고날 Aggregate 호환, DynamoDB/ScyllaDB 규모 아님 |
| **읽음 처리** | `CHAT_READ_MARKER` per-user `lastReadMessageId` | O(1) 증가, row lock 동시성, unread 인덱스 활용 |
| **채팅↔거래 결합** | `TRANSACTION_TICKET` 패턴 (ADR-006) | 당근페이 검증 패턴, 진입점 확장성, 딥링크 안전 |

---

## 5. 사용자 확정 결정 사항 (Phase 1 MVP 블로커)

2026-04-15 대화에서 옵션 1 방식으로 결정:

| # | 결정 | 선택 |
|---|---|---|
| 1 | 그룹 채팅 지원 | 1:1만 |
| 2 | 채팅방 생성 트리거 | 쇼케이스 "채팅하기" 버튼 단일 진입점 |
| 3 | 읽음 표시 시점 | 채팅방 진입 시 |
| 4 | 메시지 삭제 정책 | 본인 메시지만 soft delete |
| 5 | 시스템 메시지 스키마 | ChatMessage 확장 (`message_type` + `payload_json`) |
| 6 | 딥링크 스킴 | `gearshow://` |
| 7 | 이미지 첨부 시점 | Phase 4 (MVP 제외) |
| MVP | 범위 | **B 옵션** — 채팅 + 티켓 + DIRECT 거래 |

---

## 6. Phase 로드맵

### Phase 1 — 채팅 MVP (예상 2~3주)
**목표**: 1:1 텍스트 채팅, 실시간 송수신, 영구 저장, 읽음 처리, unread count

**범위**:
- Spring WebSocket + STOMP 설정
- `chat_room`, `chat_message`, `chat_read_marker` 테이블 구현
- 단일 서버 전제 (Kafka 연동은 Phase 2)
- 채팅방 목록·상세·메시지 CRUD (8-1 ~ 8-8)
- **제외**: 이미지, 타이핑, 그룹

**AC**:
```bash
cd backend && ./gradlew build
# Cucumber: 채팅방 생성 / 메시지 송수신 / unread 계산 / 삭제 소프트
```

### Phase 2 — 신뢰성 (1~2주)
- Kafka topic `chat.messages` 연동
- 메시지 `seq` 부여 (채팅방 내 단조 증가)
- 클라이언트 재연결 시 `since_seq` delta 동기화
- Outbox 패턴으로 Kafka at-least-once 보장
- `clientMessageId` 멱등성 처리

### Phase 3 — 오프라인 푸시 (1~2주)
- FCM Firebase 프로젝트 개설 (Android)
- APNs 인증서 (iOS 배포 시) — Apple Developer 가입 결정 필요
- `push.notifications` Kafka topic
- 유저별 토큰 관리, 알림 설정 (ON/OFF, 방해금지 시간)

### Phase 4 — 품질 (1주)
- 이미지 첨부 (S3 presigned URL 재활용)
- 신고·차단
- 읽음 표시 UX 개선

### Phase 5 — Transaction Ticket (1~2주)
- `transaction-ticket` Bounded Context 신설
- 발급·조회·취소·소비 API (9-1 ~ 9-4)
- 만료 배치 스케줄러
- 채팅방에 SYSTEM_TICKET_ISSUED 메시지 자동 삽입 (Kafka 이벤트 구독)

### Phase 6 — DIRECT 거래 완결 (1주)
- `transaction` BC에서 티켓 소비로 `TRANSACTION(DIRECT, COMPLETED)` 생성
- 쇼케이스 `SOLD` 상태 전이 연동
- 채팅방 `CLOSED` 전환

### (B 범위 여기까지) — MVP 종료

### Phase 7+ (미래) — ESCROW + PAYMENT
- Toss / Kakao PG 연동
- `payment` BC
- 환불·정산·에스크로 로직
- 이건 별도 프로젝트 수준 대공사

---

## 7. 의식적으로 미뤄둔 결정 (옵션 1 방침)

나중에 자연스러운 시점에 결정:

### Phase 5 직전 (Transaction Ticket)
- 티켓 유효기간 (15분 / 1시간 / 24시간) — 추천: 1시간
- 재사용 허용 여부 — 추천: 1회용
- 티켓 취소 주체 (발급자만 vs 양쪽) — 추천: 발급자만
- 티켓 서비스 위치 (별도 BC vs transaction 내부) — 추천: 별도 BC

### Phase 3 직전 (오프라인 푸시)
- iOS 지원 여부 (Apple Developer $99/년)
- 푸시 벤더 조합 (FCM만 vs FCM+APNs+Kakao fallback)

### Phase 7 직전 (ESCROW 시작)
- PG사 우선순위 (Toss vs Kakao)
- 에스크로 자동 정산 조건
- 취소 페널티 정책
- 리뷰/평점 시스템

### 기타 미결
- 채팅 Redis 도입 시점 (지금은 MySQL+Kafka로 충분)
- WebSocket 분산 세션 레지스트리 (수평 확장 필요 시)

---

## 8. 본 리서치의 트레젝토리 활용

`scripts/start-task.sh --with-context`를 쓰면 향후 **채팅 관련 신규 작업 시** 이 문서가 EXEC_PLAN 부록으로 자동 참조된다.

`/review-gap-analysis` 월 스케줄러가 실제 구현 결과와 이 문서의 결정을 비교하여:
- 실제로 필요 없었던 추상화 (과잉 설계 발견)
- 빠진 규칙 (구현하며 뒤늦게 추가된 것)
- 리서치가 놓친 중요 결정 축

을 다음 라운드 리서치에 반영한다.

## 9. 참고 자료 통합 인덱스

- 📄 당근페이 발표 원문 요약: `docs/research/pdf-summary-daangn-pay-2025.md` (향후 추가)
- 🏛️ ADR-005 (프로토콜), ADR-006 (티켓 패턴), ADR-007 (BC 경계)
- 📋 `docs/business/biz-logic.md §7 CHAT`, `§8 TRANSACTION`
- 🔌 `docs/spec/api-spec.md §8 CHAT`, `§9 TRANSACTION TICKET`
- 🗃️ `docs/diagram/schema.md` — CHAT_MESSAGE, CHAT_READ_MARKER, TRANSACTION_TICKET
