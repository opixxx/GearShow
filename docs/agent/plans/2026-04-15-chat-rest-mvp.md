# EXEC_PLAN: chat-rest-mvp

- **Type**: feature
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Risky
- **Created**: 2026-04-15 16:57
- **Branch**: feature/chat-rest-mvp
- **Worktree**: /Users/opix/GearShow/../gearshow-chat-rest-mvp
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

쇼케이스 채팅 기능 Phase 1 MVP 중 **REST 슬라이스**를 구현한다. WebSocket/STOMP·Kafka·푸시·티켓·거래 결합은 제외하고, 1:1 채팅방 CRUD와 텍스트 메시지 송수신·읽음 처리·soft delete를 HTTP 엔드포인트로만 완성하여 이후 WebSocket 레이어가 얹힐 안정된 기반을 만든다.

## 2. 범위 (Scope)

### In
- 신규 Bounded Context `chat` 추가 (`backend/src/main/java/com/gearshow/backend/chat/**`)
- 엔티티 3종: `ChatRoom`, `ChatMessage`, `ChatReadMarker` (schema.md 정의 준수, ddl-auto로 자동 생성)
- REST 엔드포인트 7종 (api-spec.md §8):
  - 8-1 `GET /api/v1/chat-rooms` (커서 기반 목록 + unread count)
  - 8-2 `GET /api/v1/chat-rooms/{id}` (상세)
  - 8-3 `POST /api/v1/chat-rooms` (생성 또는 기존 반환, idempotent)
  - 8-4 `GET /api/v1/chat-rooms/{id}/messages` (커서 기반 히스토리)
  - 8-5 `POST /api/v1/chat-rooms/{id}/messages` (TEXT 송신 + `clientMessageId` 멱등성)
  - 8-7 `POST /api/v1/chat-rooms/{id}/read` (lastReadMessageId 갱신, row lock)
  - 8-8 `DELETE /api/v1/chat-rooms/{id}/messages/{messageId}` (본인 메시지 soft delete)
- Cucumber 인수 테스트: 채팅방 생성·메시지 송수신·unread 계산·soft delete
- ArchUnit 규칙 적용 (기존 패턴 유지)
- biz-logic §7, api-spec §8 중 REST에 해당하는 부분만 반영

### Out
- WebSocket/STOMP/SockJS 설정 및 `/ws`, `/topic`, `/app` 라우팅 (다음 worktree `feature/chat-ws`)
- Kafka 연동·메시지 `seq` 분산 보장 (Phase 2)
- 이미지 메시지(`messageType=IMAGE`) (Phase 4)
- `SYSTEM_*` 메시지 타입 및 `payloadJson` 실제 사용 (티켓/거래 Phase 5~6) — 컬럼은 schema대로 존재하되 본 PR에서는 `TEXT`만 송신 가능
- 오프라인 푸시 (Phase 3)
- 쇼케이스 `SOLD`/`DELETED` 전이 시 채팅방 `CLOSED` 자동 전환 (해당 전이를 발생시키는 코드 변경은 본 범위 외 — 단 `CLOSED` 상태의 읽기 가능·쓰기 차단 규칙은 구현)
- `since_seq` delta 동기화 파라미터 (WebSocket 재연결용, 현재 불필요)

## 3. 변경 대상 (Affected)

- **domain/**:
  - `chat/domain/model/ChatRoom.java`, `ChatMessage.java`, `ChatReadMarker.java`
  - `chat/domain/vo/ChatRoomStatus.java`, `ChatMessageType.java`, `ChatMessageStatus.java`
  - `chat/domain/exception/NotFoundChatRoomException.java`, `ForbiddenChatRoomAccessException.java`, `ChatRoomOwnShowcaseException.java`, `ChatRoomShowcaseNotAvailableException.java`, `ChatMessageTooLongException.java`, `ChatRoomClosedException.java`, `DuplicateClientMessageIdException.java`, `ChatMessageNotOwnerException.java`, `ChatMessageSystemUndeletableException.java`, `NotFoundChatMessageException.java`
- **application/**:
  - InputPort: `ListChatRoomsUseCase`, `GetChatRoomUseCase`, `CreateOrGetChatRoomUseCase`, `ListChatMessagesUseCase`, `SendChatMessageUseCase`, `MarkChatRoomReadUseCase`, `DeleteChatMessageUseCase`
  - OutputPort: `ChatRoomPort`, `ChatMessagePort`, `ChatReadMarkerPort`, `ShowcaseReadPort` (showcase BC로부터 title/thumbnail/sellerId/상태 조회), `UserReadPort` (peer nickname/profileImageUrl 조회) — 기존 BC 어댑터 재사용 가능하면 재사용
  - Service: `ListChatRoomsService`, `GetChatRoomService`, `CreateOrGetChatRoomService`, `ListChatMessagesService`, `SendChatMessageService`, `MarkChatRoomReadService`, `DeleteChatMessageService`
  - DTO/Command/Result: 각 UseCase 페어
- **adapter/**:
  - in/web: `ChatRoomController`, `ChatMessageController`, `ChatReadController` + 요청/응답 DTO
  - out/persistence: `ChatRoomJpaEntity`, `ChatMessageJpaEntity`, `ChatReadMarkerJpaEntity`, 각 `*JpaRepository`, `*Mapper`, `*PersistenceAdapter`
  - out/showcase, out/user: 필요 시 `ShowcaseReadPort`/`UserReadPort` 구현 어댑터 (이미 존재하는 Service를 주입하거나, 신규 OutAdapter로 분리 — 헥사고날 경계 존중)
- **docs/**:
  - `docs/business/biz-logic.md` — §7 규칙 중 변경분 없음 확인만, 없으면 수정 없음
  - `docs/agent/trajectories/2026-04.log` — Post-merge에 자동 기록

## 4. 접근 (Approach)

**BC 경계 (ADR-007 준수)**:
- `chat` → `showcase`, `user` 로 **읽기 전용 단방향 의존**. 반대 의존 금지.
- 의존은 **Application OutputPort**를 통해서만. `chat/adapter`가 `showcase/domain` 또는 `user/domain`을 직접 import하지 않는다.

**Aggregate 설계**:
- `ChatRoom` Aggregate 루트. `ChatMessage`, `ChatReadMarker`는 같은 Aggregate 내부 엔티티이나 메시지 볼륨상 **별도 Repository**를 사용해 지연 로딩한다. (schema.md가 명시: FK는 같은 Aggregate, 인덱스 `(chatRoomId, seq)`).

**멱등성 (8-3, 8-5)**:
- 8-3 `createOrGet`: `(showcaseId, buyerId)` UNIQUE 제약 + 서비스 내 "조회 → 없으면 생성" 패턴. 동시성 충돌 시 `DataIntegrityViolationException` 잡아 재조회로 수렴. 200 vs 201 구분하여 반환.
- 8-5 `clientMessageId`: `(chatRoomId, senderId, clientMessageId)` 유니크 인덱스 추가 (schema.md에 없다면 엔티티 레벨 유니크 제약으로). 중복이면 기존 메시지 id를 조회해 **409가 아니라 기존 값을 반환**. api-spec은 "409 반환, 응답은 기존 메시지"라고 되어 있으므로 **409 + 기존 메시지 바디**로 구현.
- `clientMessageId` 컬럼은 schema.md에 명시가 없어 **본 PR에서 `chat_message`에 추가**한다 (nullable `varchar(64)` + 인덱스). 스키마 변경 사항으로 schema.md도 함께 갱신.

**seq 부여**:
- 단일 서버 전제 → `SELECT MAX(seq) FROM chat_message WHERE chat_room_id=? FOR UPDATE` + `+1`. 트랜잭션 내 row lock으로 동시성 차단. (Kafka는 Phase 2에서 변경)
- `ChatRoom.lastMessageAt`은 메시지 생성/삭제 시 갱신.

**unread count**:
- 목록 조회에서 N+1 회피: 채팅방 ID 묶어 `chat_message` + `chat_read_marker` 조인으로 한 번에 계산. 네이티브 SQL 또는 JPQL GROUP BY. 구현체는 에이전트 재량.

**권한 체크 (양보 불가)**:
- 모든 채팅 API는 인증 필요. 현재 로그인 유저가 `ChatRoom.sellerId`나 `buyerId` 중 하나여야 한다. 아니면 `403 FORBIDDEN_CHAT_ROOM_ACCESS`.
- 메시지 삭제: `senderId == 현재 유저` 여야 한다. 시스템 메시지(`senderId IS NULL`)는 삭제 금지.

**CLOSED 상태 규칙**:
- 8-1/8-2/8-4: 읽기 허용.
- 8-5 송신: `403 CHAT_ROOM_CLOSED`.
- 8-7 읽음 처리: 허용 (과거 메시지 읽음 갱신 가능).
- 8-8 삭제: 허용 (본인 메시지).

**트랜잭션 범위 (database-optimizer 대비)**:
- Command 서비스는 `@Transactional`, Query 서비스는 `@Transactional(readOnly = true)`.
- 메시지 전송 서비스는 외부 BC 호출(showcase 상태 확인)을 트랜잭션 **진입 전** 또는 **읽기 전용 호출**로 제한.

**예외 매핑**:
- `@RestControllerAdvice` 기존 글로벌 핸들러에 ErrorCode 등록 (exception-rules.md 준수).
- 새 ErrorCode: `FORBIDDEN_CHAT_ROOM_ACCESS(403)`, `CHAT_ROOM_OWN_SHOWCASE(400)`, `CHAT_ROOM_SHOWCASE_NOT_AVAILABLE(400)`, `CHAT_MESSAGE_TOO_LONG(400)`, `CHAT_ROOM_CLOSED(403)`, `DUPLICATE_CLIENT_MESSAGE_ID(409)`, `CHAT_MESSAGE_NOT_OWNER(403)`, `CHAT_MESSAGE_SYSTEM_UNDELETABLE(400)`, `NOT_FOUND_CHAT_ROOM(404)`, `NOT_FOUND_CHAT_MESSAGE(404)`.

**커서 페이지네이션**:
- 목록(8-1): 복합 커서 `(lastMessageAt DESC, chatRoomId DESC)` base64 인코딩 JSON. 기존 프로젝트 커서 유틸이 있으면 재사용.
- 메시지 히스토리(8-4): `before={messageId}` 쿼리 → `chatMessageId < before` DESC 정렬 후 응답 역순 여부는 클라이언트 기대에 맞춰 **오래된 것 → 최신** 순으로 응답.

## 5. 단계 (Steps)

### Step 1: chat-domain

**읽어야 할 파일**:
- `docs/business/biz-logic.md` §7 (규칙 전반)
- `docs/diagram/schema.md` CHAT_ROOM / CHAT_MESSAGE / CHAT_READ_MARKER 절
- `docs/architecture/adr/ADR-005-chat-protocol.md`, `ADR-007-chat-transaction-payment-boundaries.md`
- 기존 도메인 참고: `backend/src/main/java/com/gearshow/backend/showcase/domain/model/*` — 순수 도메인 스타일 확인

**작업**:
- 패키지 `com.gearshow.backend.chat.domain`
- `ChatRoom` Aggregate 루트 (팩토리 메서드 `open(showcaseId, sellerId, buyerId)`, 상태 전이 `close()`, `send()` 정책 검증)
- `ChatMessage` 엔티티 (팩토리 메서드 `text(chatRoomId, senderId, seq, content, clientMessageId)`, `softDelete(requesterId)` 정책 포함)
- `ChatReadMarker` 엔티티 (`updateTo(lastReadMessageId)`)
- VO `enum ChatRoomStatus {ACTIVE, CLOSED}`, `ChatMessageType {TEXT, IMAGE, SYSTEM_TICKET_ISSUED, SYSTEM_TRANSACTION_STARTED, SYSTEM_PAYMENT_COMPLETED, SYSTEM_TRANSACTION_COMPLETED, SYSTEM_TRANSACTION_CANCELLED}`, `ChatMessageStatus {ACTIVE, DELETED}`
- 도메인 예외 10종 (§3 목록) — `RuntimeException` 기반, 한글 메시지.
- 양보 불가 규칙: `domain/` 패키지에 Spring/JPA import 금지. Lombok도 금지(프로젝트 기본 컨벤션 확인 후 따름).

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew archTest
```

**금지사항**:
- `domain/`에서 `javax.persistence`·`jakarta.persistence`·`org.springframework.*` import 금지. 이유: 헥사고날 경계 위반, ArchUnit이 차단.
- `ChatMessage`에 `senderId == null` + `messageType == TEXT` 조합 허용 금지. 이유: TEXT는 반드시 사용자 발신.

### Step 2: chat-application-ports

**읽어야 할 파일**:
- Step 1 산출물 전체
- `backend/src/main/java/com/gearshow/backend/showcase/application/port/**` — 포트 명명/시그니처 컨벤션

**작업**:
- 패키지 `com.gearshow.backend.chat.application.port.{in,out}`
- InputPort 7종 (§3): 각각 `execute(Command)` 또는 `execute(Query)` 단일 메서드
- OutputPort:
  - `ChatRoomPort` — `save`, `findById`, `findByShowcaseIdAndBuyerId`, `listByParticipant(userId, cursor, size)` (unread count 포함 Projection)
  - `ChatMessagePort` — `save`, `findById`, `findByChatRoomIdAndClientMessageId`, `nextSeq(chatRoomId)` (내부는 MAX+1 FOR UPDATE), `listBeforeMessageId(chatRoomId, before, size)`
  - `ChatReadMarkerPort` — `findByChatRoomIdAndUserId`, `save`, `upsert(chatRoomId, userId, lastReadMessageId)` (row lock)
  - `ShowcaseReadPort` — `getSummary(showcaseId) → {sellerId, title, thumbnailUrl, status}` — showcase BC의 기존 Application Service를 감싸는 어댑터로 구현 예정 (Step 4에서)
  - `UserReadPort` — `getProfile(userId) → {nickname, profileImageUrl}`
- Command/Result/Query 레코드 (한글 Javadoc)

**AC**:
```bash
cd backend
./gradlew compileJava
```

**금지사항**:
- Port 메서드에서 JPA 엔티티 노출 금지. 오직 도메인 모델·VO·Projection record 만.
- `ShowcaseReadPort`/`UserReadPort`가 showcase·user의 도메인 모델을 직접 반환 금지. 독립 Projection record 정의.

### Step 3: chat-application-services

**읽어야 할 파일**:
- Step 1, 2 산출물
- `backend/src/main/java/com/gearshow/backend/showcase/application/service/*` — 트랜잭션/예외 처리 스타일
- `docs/spec/api-spec.md` §8-1 ~ §8-8 (WebSocket 8-6 제외)
- `.claude/skills/orchestrator/references/exception-rules.md`

**작업**:
- 7개 서비스 구현. 각 서비스는 InputPort 구현체 + `@Service` + `@Transactional`(읽기는 `readOnly`).
- 양보 불가 규칙:
  - 8-3: `(showcaseId, buyerId)` 조회 → 있으면 200 의미의 Result + existed=true 리턴, 없으면 showcase 검증 → 생성 → 201 의미의 Result + existed=false 리턴. 자기 자신의 쇼케이스면 `ChatRoomOwnShowcaseException`. 쇼케이스 상태 `DELETED`/`SOLD`면 `ChatRoomShowcaseNotAvailableException`.
  - 8-5: 권한 검증 → `ChatRoom.status == ACTIVE` 검증 → content 길이 2000 초과 시 `ChatMessageTooLongException` → `clientMessageId` 조회, 중복이면 `DuplicateClientMessageIdException`에 기존 메시지 id 포함 → `nextSeq` → `ChatMessage.text(...)` → 저장 → `ChatRoom.lastMessageAt` 갱신 → Result 반환.
  - 8-7: `upsert(chatRoomId, userId, lastReadMessageId)` 호출. 메시지가 존재하는지 검증(`NotFoundChatMessageException`).
  - 8-8: 메시지 조회 → `senderId == 현재 유저` 아니면 `ChatMessageNotOwnerException` → 시스템 타입이면 `ChatMessageSystemUndeletableException` → `softDelete()` 호출 → 저장.
- unread count 계산은 Port Projection에 위임. 서비스는 조립만.

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew test --tests "com.gearshow.backend.chat.application.*" || true  # 유닛 테스트 없어도 통과
```

**금지사항**:
- 서비스에서 `ChatRoom`의 내부 상태를 외부에서 setter로 변경 금지. 도메인 메서드로만. 이유: 불변식 캡슐화.
- 컨트롤러가 넘긴 sellerId/buyerId를 그대로 신뢰 금지. 서버 세션에서 꺼낸 `authUserId`를 기준으로.

### Step 4: chat-adapter-persistence

**읽어야 할 파일**:
- Step 1, 2 산출물
- `backend/src/main/java/com/gearshow/backend/showcase/adapter/out/persistence/*` — JpaEntity/Mapper/PersistenceAdapter 컨벤션
- `docs/diagram/schema.md` 인덱스·유니크 정의

**작업**:
- `ChatRoomJpaEntity` (UNIQUE `(showcase_id, buyer_id)`), `ChatMessageJpaEntity` (인덱스 `(chat_room_id, seq)`, UNIQUE `(chat_room_id, sender_id, client_message_id)`), `ChatReadMarkerJpaEntity` (UNIQUE `(chat_room_id, user_id)`)
- 각 `*JpaRepository extends JpaRepository<..., Long>`
- Mapper: 도메인 ↔ JpaEntity 양방향
- PersistenceAdapter: Port 구현체.
- `nextSeq` 는 native query `SELECT COALESCE(MAX(seq),0) FROM chat_message WHERE chat_room_id=:id FOR UPDATE` + 1.
- `upsert` 는 `ChatReadMarker`를 `SELECT ... FOR UPDATE` 후 `updateTo()` 도메인 메서드로 갱신, 없으면 신규 저장.
- 목록 조회 unread count: `@Query`로 `chat_room` LEFT JOIN `chat_message` (marker 기준) 후 Projection record 매핑. N+1 금지.
- `ShowcaseReadPort` 구현 어댑터: `com.gearshow.backend.chat.adapter.out.showcase.ShowcaseReadAdapter` — showcase BC의 기존 `GetShowcaseUseCase`/`ShowcasePort`를 의존해 Projection으로 변환. 단 `chat` 패키지가 `showcase/domain/*` 를 import하지 않도록, showcase Application의 **공개 Result DTO**만 사용.
- `UserReadPort` 구현 어댑터: 유사하게 `user` BC의 공개 Result에서 변환.
- 스키마는 ddl-auto로 자동 생성 (Flyway 미사용).

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew archTest
./gradlew test --tests "*ChatPersistenceAdapterTest*" || true
```

**금지사항**:
- JPQL에서 도메인 패키지 타입 참조 금지. 오직 JpaEntity 타입만. 이유: 도메인 오염 방지.
- `@Transactional`을 PersistenceAdapter에 직접 달지 말 것. 이유: 트랜잭션 범위는 application 레이어 책임.

### Step 5: chat-adapter-web

**읽어야 할 파일**:
- Step 2, 3 산출물
- `backend/src/main/java/com/gearshow/backend/showcase/adapter/in/web/*Controller.java` — 응답 래퍼/인증 주입 패턴
- 기존 `@RestControllerAdvice` 전역 예외 핸들러 위치

**작업**:
- `ChatRoomController` (`/api/v1/chat-rooms`): 목록/상세/생성
- `ChatMessageController` (`/api/v1/chat-rooms/{roomId}/messages`): 목록/송신/삭제
- `ChatReadController` (`/api/v1/chat-rooms/{roomId}/read`): 읽음
- 요청 DTO: `CreateChatRoomRequest(showcaseId)`, `SendChatMessageRequest(messageType, content, clientMessageId)`, `MarkReadRequest(lastReadMessageId)`
- 응답 DTO: `ChatRoomListItemResponse`, `ChatRoomDetailResponse`, `ChatRoomIdResponse`, `ChatMessageItemResponse`, `SendChatMessageResponse`
- Bean Validation 한글 메시지 (`@NotNull`, `@Size(max=2000)` 등)
- ErrorCode 등록 및 GlobalExceptionHandler에 신규 예외 매핑 (§4 목록).
- 인증 유저 ID는 기존 컨벤션(예: `@AuthenticationPrincipal`)으로 주입.
- 8-3 응답 status code 처리: 신규 생성 시 201, 기존 반환 시 200 — `ResponseEntity` 로 분기.
- 8-5 `DuplicateClientMessageIdException` 는 409로 매핑하되 응답 바디에 기존 메시지 id·seq·sentAt 포함.

**AC**:
```bash
cd backend
./gradlew compileJava
./gradlew test --tests "*ChatRoomControllerTest*" --tests "*ChatMessageControllerTest*" || true
```

**금지사항**:
- Controller에서 도메인/JpaEntity 노출 금지. 오직 Application Result → Web Response 변환.
- 쿼리 파라미터를 그대로 Service에 넘기지 말고 Command/Query record로 감싸기. 이유: 경계 명확화.

### Step 6: chat-tests

**읽어야 할 파일**:
- `.claude/skills/orchestrator/references/test-rules.md`
- 기존 Cucumber 설정: `backend/src/test/resources/features/**`, Step Definitions 위치
- Step 1~5 산출물 전체

**작업**:
- Cucumber `.feature`: `chat-rest-mvp.feature` — 시나리오 최소 5개
  1. 구매자가 쇼케이스에서 채팅하기를 눌러 새 채팅방을 생성한다 (201)
  2. 같은 쇼케이스·같은 구매자가 다시 눌러도 기존 채팅방이 반환된다 (200)
  3. 판매자와 구매자가 메시지를 주고 받고 unread count가 정확히 계산된다
  4. 채팅방 진입 시 읽음 처리 후 unread가 0이 된다
  5. 본인 메시지를 soft delete 하면 이후 목록에서 "삭제된 메시지입니다" 플레이스홀더로 조회된다
- Unhappy path 시나리오 최소 3개:
  6. 자기 쇼케이스에 채팅 시도 → 400 `CHAT_ROOM_OWN_SHOWCASE`
  7. 2000자 초과 메시지 → 400 `CHAT_MESSAGE_TOO_LONG`
  8. 남의 메시지 삭제 시도 → 403 `CHAT_MESSAGE_NOT_OWNER`
- 단위 테스트: `ChatRoom`, `ChatMessage` 도메인 정책 (권한, 상태 전이, 길이 제한) — 핵심만.
- Persistence 슬라이스 테스트(@DataJpaTest) 없이 통합 테스트로 커버 가능하면 생략.
- ArchUnit: 기존 모듈 경계 규칙에 `chat` 추가. 없다면 신규 규칙 작성.

**AC**:
```bash
cd backend
./gradlew build   # compileJava + test + jacocoTestCoverageVerification + archTest 모두 포함
```

**금지사항**:
- 테스트에서 production 코드를 수정해 통과시키는 패턴 금지 (가시성 완화 등). 이유: 오염.
- Thread.sleep, 외부 네트워크 호출 금지. 이유: 불안정 테스트.

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: Cucumber 시나리오 1~5 (채팅방 생성/조회/메시지 송수신/unread 계산/읽음/soft delete).
- **Unhappy Path**: Cucumber 시나리오 6~8 (자기 쇼케이스/길이 초과/타인 삭제) + 단위 테스트로 권한·상태 전이.
- **추가 검증**:
  - ArchUnit: `chat.domain` → Spring/JPA 미의존, `chat.adapter` → `chat.application.port` 로만 참조, `chat` → `showcase.domain`/`user.domain` 직접 의존 금지.
  - 멱등성 테스트: 동일 `clientMessageId`로 2회 연속 POST → 409 + 기존 메시지 바디.
  - 동시성 sanity: `createOrGet` 동일 요청 2회 동시 호출 시 결과 1개 수렴 (간단 CountDownLatch 기반 1건 단위 테스트).

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

모든 step 완료 후 다음이 모두 통과해야 `Status: completed` 로 마무리:

```bash
cd backend
./gradlew build           # 컴파일 + 전체 테스트 + 커버리지(70%) + ArchUnit
```

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건
- [ ] architecture-reviewer Critical 지적 0건
- [ ] database-optimizer Critical 지적 0건 (Repository 변경 있음)
- [ ] `docs/spec/api-spec.md` §8-6 WebSocket 섹션에 "Phase 1 MVP REST 슬라이스에서 제외" 주석 없으면 추가 불필요. `clientMessageId` 유니크 키 추가 사실을 `docs/diagram/schema.md`에 반영
- [ ] EXEC_PLAN의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

- **코드**: `git revert <merge-commit>` 또는 `feature/chat-rest-mvp` 브랜치 미머지 처리.
- **DB**: ddl-auto 기반이므로 자동 마이그레이션 스크립트는 없지만, 운영 환경에 배포된 경우 아래 DDL로 수동 드롭:
  ```sql
  DROP TABLE IF EXISTS chat_read_marker;
  DROP TABLE IF EXISTS chat_message;
  DROP TABLE IF EXISTS chat_room;
  ```
  위 3테이블은 신규이므로 기존 데이터 영향 없음. 드롭 전 `chat_message` 백업 권장 (운영 반영 이후 롤백 시).
- **실행 순서**: 코드 revert → 애플리케이션 재배포 확인 → 필요 시 DDL 수동 실행.
- **데이터 손실**: 채팅 데이터 전부 소실. 신규 기능이므로 사용자 피해는 배포 후 대화량에 비례. 초기 릴리스 직후 롤백이 가장 안전.

---

## ⚠️ 작성 규칙 요약

1. **자기완결성**: 외부 대화 참조 금지. 필요한 모든 정보를 명시.
2. **시그니처 수준 지시**: "어떻게 구현하라" 보단 "무엇을, 어떤 인터페이스로". 구현체는 에이전트 재량.
3. **AC는 Bash 커맨드**: "동작해야 한다" 같은 추상 서술 금지. 실행 가능한 명령으로.
4. **금지사항은 구체적으로**: "조심해라" 대신 "X를 하지 마라. 이유: Y" 형식.
5. **Step 분할**: Scope 최소화 — 한 step에 한 레이어/모듈. 여러 모듈 동시면 step 쪼개기.

> 이 EXEC_PLAN의 모든 TODO 마커가 채워질 때까지 코드 편집이 차단됩니다 (`enforce-plan.sh`).

---

## 부록 — 관련 컨텍스트 (자동 첨부)

> 이전 작업의 패턴·마찰점 + 관련 리서치·ADR 힌트를 새 작업 시작 시점에 주입.
> --with-context 옵션으로 활성화. 참고용.

### 관련 리서치 문서 (최신 5개)

이 task와 관련성이 있어 보이면 먼저 읽어라. 파일명에 주제 키워드가 있으면 우선순위:

- `docs/research/2026-04-15-chat-design.md`

### ADR 문서

3개의 ADR이 존재한다. 이 task와 관련된 주제의 ADR이 있는지 확인:

- `docs/architecture/adr/ADR-005-chat-protocol.md`
- `docs/architecture/adr/ADR-006-transaction-ticket-pattern.md`
- `docs/architecture/adr/ADR-007-chat-transaction-payment-boundaries.md`
