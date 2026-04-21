# ADR-009: 채팅 메시지 브로드캐스트 경로 — Outbound Port + AFTER_COMMIT 리스너

- **상태**: Accepted
- **결정일**: 2026-04-22
- **관련 PR**: fix/pr32-phase1
- **결정 주체**: GearShow Backend

---

## 1. 배경 (Context)

PR #32 초기 구현에서 새 메시지 발송 시 브로드캐스트 로직이 REST 컨트롤러(`ChatMessageController`) 와 WebSocket 컨트롤러(`ChatWebSocketController`) 양쪽에 **중복** 되어 있었다.

```java
// 두 컨트롤러 모두 아래 3줄을 복제 보유
StompChatMessageResponse ws = StompChatMessageResponse.of(
    result.chatMessageId(), chatRoomId, userId, result.seq(),
    messageType, content, null, result.sentAt());
messagingTemplate.convertAndSend("/topic/chat-rooms/" + chatRoomId, ws);
```

리뷰 결과 세 가지 구조적 문제가 식별되었다.

1. **Shotgun Surgery**: 필드 추가·토픽 경로 변경 시 두 곳을 동시에 수정해야 한다. 한쪽을 빼먹으면 REST 경로와 WebSocket 경로의 wire format 이 달라져 추적이 어렵다.
2. **유령 메시지 리스크**: 컨트롤러 메서드에 누군가 `@Transactional` 을 얹으면 `UseCase.send` 가 아직 커밋되지 않은 상태에서 브로드캐스트가 나간 후 트랜잭션이 롤백될 수 있다 → "DB엔 없는데 클라이언트엔 전달된" 메시지.
3. **어댑터 간 결합**: REST 어댑터(`chat/adapter/in/web`) 가 WebSocket 어댑터(`chat/adapter/in/websocket/dto`) 의 DTO 를 import 하고 있었다. 수평 어댑터 간 의존은 헥사고날 원칙상 피해야 한다.

## 2. 결정 (Decision)

chat 컨텍스트에 **브로드캐스트 아웃바운드 포트** 를 신설하고, 저장 트랜잭션 커밋 후 실행되는 **이벤트 리스너** 만이 포트를 호출한다.

```
SendChatMessageService  (@Transactional)
  ├─ chatMessagePort.save(...)
  ├─ chatRoomPort.touchLastMessageAt(...)
  └─ applicationEventPublisher.publishEvent(ChatMessageCreatedEvent)
                                                           │
                                                           ▼ (커밋 성공 시에만)
                              ChatMessageBroadcastListener
                              @TransactionalEventListener(AFTER_COMMIT)
                                                           │
                                                           ▼
                              ChatMessageBroadcastPort.publish(...)
                                                           │
                                                           ▼
                              StompBroadcastAdapter
                                convertAndSend("/topic/chat-rooms/{id}", ...)
```

두 컨트롤러(`ChatMessageController`, `ChatWebSocketController`)는 `SimpMessagingTemplate` 에 대한 의존을 제거하고 `SendChatMessageUseCase.send(...)` 만 호출한다.

### 양보 불가 규칙

- `chat/adapter/in/**` 에서 `org.springframework.messaging.simp.SimpMessagingTemplate` import **0건**
- chat 컨텍스트 내 `SimpMessagingTemplate` 직접 의존은 오직 `chat/adapter/out/broadcast/StompBroadcastAdapter` **한 파일**
- 브로드캐스트는 `@TransactionalEventListener(phase = AFTER_COMMIT)` 경유로만 수행 (직접 `port.publish()` 호출 금지)
- `@TransactionalEventListener(fallbackExecution = true)` 금지 — 트랜잭션 없는 호출 컨텍스트 발생 시 의도치 않은 브로드캐스트 방지

## 3. 고려한 대안 (Alternatives)

### A. Service 내부에서 `port.publish()` 직접 호출

- 장점: 가장 단순 (이벤트 클래스 불필요).
- 단점: **유령 메시지 리스크 해결 못함**. 트랜잭션 커밋 전 publish 가 실행되므로 롤백 시 클라이언트에는 이미 도달한 메시지가 DB 에는 없다. 이 PR 이 푸는 핵심 문제를 풀지 못한다.
- 판단: **기각**.

### B. Kafka 를 즉시 도입 (Phase 3 계획 앞당김)

- 장점: 수평 확장 · 다중 인스턴스 브로커 문제(`enableSimpleBroker` 한계) 도 동시 해결.
- 단점: Kafka Consumer · 토픽 설계 · 장애 복구 시나리오 등 구현 범위 급증. Phase 1 예산 초과.
- 판단: **별도 Phase 3** 에서 처리. 현재 포트는 향후 `KafkaBroadcastAdapter` 로 구현 교체만 하면 됨.

### C. 도메인 이벤트로 분리 (`ChatMessageCreated` 를 도메인 패키지에)

- 장점: 도메인 중심 이벤트 모델링 원칙 부합.
- 단점: 현 이벤트는 "브로드캐스트 payload" 의 컨테이너일 뿐 비즈니스 의미(예: "송신됨" 상태 전이) 가 아니다. 도메인 패키지 오염.
- 판단: **기각**. Application event (transport-layer) 로 명확히 구분.

## 4. 결과 (Consequences)

### 긍정

- 브로드캐스트 로직이 **한 곳(`StompBroadcastAdapter`)** 으로 수렴 — 필드 추가 · 토픽 변경 시 단일 수정.
- 트랜잭션 롤백 시 브로드캐스트가 **구조적으로 차단** — 유령 메시지 원천 제거.
- 컨트롤러가 전송 인프라에 의존하지 않는다(헥사고날 정합).
- `KafkaBroadcastAdapter` 등으로 전송 수단 교체 시 **포트 구현만 갈아끼면 끝**.
- `StompChatMessageResponse.of(...)` 8-params 팩토리를 `from(ChatMessageBroadcastPayload)` 하나로 통일 — 인자 순서 실수 위험 제거.

### 부정

- 신규 클래스 5개 추가 (`Port` · `Payload` · `Event` · `Listener` · `Adapter`) — 구조 비용.
- 이벤트-리스너 모델을 새 개발자에게 설명하는 비용.
- 리스너 예외 발생 시 사용자 세션에는 브로드캐스트가 누락된다. 현 정책: 로그만 남기고 재접속/다음 메시지 시점의 동기화로 자연 회복 (금융 수준 보장 필요 시 DLQ · 재시도 도입).

### 검증

- ArchUnit: `chat/adapter/in/**` → `SimpMessagingTemplate` 의존 0건 (StompBroadcastAdapter 제외).
- 통합 테스트: 저장 트랜잭션 롤백 시 리스너 미호출 검증 (별도 테스트 추가 예정).
- `./gradlew build` 통과.

## 5. 참조

- 리뷰 소스: CodeRabbit (Broadcast 중복), architecture-reviewer (BC 격리 + 컨트롤러 부수효과), code-reviewer (트랜잭션 경계 유령 메시지).
- PR #32 통합 평가 코멘트 (`#issuecomment-4289599634`) Phase 1 #5.
