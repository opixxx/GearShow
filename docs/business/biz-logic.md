# 비즈니스 로직 (Business Logic)

---

> 이 문서는 GearShow 플랫폼의 도메인별 비즈니스 규칙을 정의한다.
> `[TBD]` 표시는 확정이 필요한 항목이다.

---

## 1. USER (사용자)

### 1-1. 회원 가입 및 인증

- 소셜 로그인(Kakao, Google, Apple)을 통해서만 가입할 수 있다.
- 하나의 사용자는 여러 소셜 계정을 연동할 수 있다. (`AUTH_ACCOUNT` 1:N)
- 최초 로그인 시 자동으로 사용자 계정이 생성된다.
- 닉네임은 가입 시 필수이며, 중복될 수 없다.

### 1-2. 사용자 상태 전이

```
ACTIVE → SUSPENDED → ACTIVE   (관리자에 의한 정지/해제)
ACTIVE → WITHDRAWN             (본인 탈퇴)
```

| 상태 | 설명 |
|:----|:-----|
| ACTIVE | 정상 활동 가능 |
| SUSPENDED | 관리자에 의해 정지됨 (로그인 불가, 기존 쇼케이스 비공개 처리) |
| WITHDRAWN | 탈퇴 완료 |

### 1-3. 휴대폰 인증

- 거래 기능을 사용하려면 휴대폰 인증이 완료되어야 한다.
- 인증 코드 유효 시간: 180초
- 인증 코드는 6자리 숫자이다.
- `[TBD]` 일일 인증 요청 횟수 제한 (예: 5회/일)

### 1-4. 회원 탈퇴

- 진행 중인 거래(`PENDING`, `IN_PROGRESS`)가 있으면 탈퇴할 수 없다.
- 탈퇴 시 소유한 쇼케이스는 `DELETED` 상태로 변경된다.
- 탈퇴 시 참여 중인 채팅방은 `CLOSED` 상태로 변경된다.
- `[TBD]` 탈퇴 후 개인정보 보관 기간 (예: 30일 유예 후 영구 삭제)

---

## 2. CATALOG (카탈로그)

### 2-1. 카탈로그 아이템

- 카탈로그는 플랫폼에 등록된 공식 장비 정보이다. (축구화, 유니폼 등)
- 카테고리는 `BOOTS`, `UNIFORM`으로 구분한다.
- `modelCode`는 동일 카테고리 내에서 중복될 수 없다.
- 카테고리에 따라 하위 스펙이 결정된다.
  - `BOOTS` → `BOOTS_SPEC` (스터드 타입, 사일로, 출시 연도 등)
  - `UNIFORM` → `UNIFORM_SPEC` (클럽명, 시즌, 리그 등)

### 2-2. 카탈로그 상태

| 상태 | 설명 |
|:----|:-----|
| ACTIVE | 정상 노출, 쇼케이스 등록 가능 |
| INACTIVE | 비공개, 신규 쇼케이스 등록 불가 (기존 쇼케이스는 유지) |

- `INACTIVE` 상태의 카탈로그 아이템으로는 새 쇼케이스를 등록할 수 없다.

### 2-3. 카탈로그 등록 권한

- `[TBD]` 카탈로그 등록 권한 정책 (관리자 전용 vs 사용자 제안 + 관리자 승인)

---

## 3. SHOWCASE (쇼케이스)

### 3-1. 쇼케이스 등록

- 인증된 사용자만 쇼케이스를 등록할 수 있다.
- 카테고리(`category`)와 브랜드(`brand`)는 필수이다.
- 카탈로그 아이템 연결은 선택사항이다. (연결 시 ACTIVE 상태만 가능)
  - 카탈로그를 선택하면 `category`, `brand`, `modelCode`가 카탈로그에서 복사된다.
  - 카탈로그 없이도 사용자가 직접 `category`, `brand`, `modelCode`를 입력할 수 있다.
- 제목은 필수이다.
- 상태 등급(`conditionGrade`)은 필수이며 `S`, `A`, `B`, `C` 중 하나를 선택한다.
- 일반 이미지는 최소 1개 이상 첨부해야 한다.
- 대표 이미지(`isPrimary`)는 반드시 1개만 존재해야 한다.
- 카테고리에 따라 쇼케이스 스펙을 입력할 수 있다. (선택사항)
  - `SHOWCASE_SPEC` 단일 테이블에 `specType`과 `specData`(JSON)로 저장된다.
  - `BOOTS` → `{"studType":"FG","siloName":"Mercurial","releaseYear":"2025","surfaceType":"천연잔디"}`
  - `UNIFORM` → `{"clubName":"Liverpool","season":"24-25","league":"EPL","kitType":"HOME"}`
  - 새 카테고리 추가 시 테이블 스키마 변경 없이 specType 값과 JSON 구조만 추가한다.
- 대표 이미지 URL(`primaryImageUrl`)과 3D 모델 보유 여부(`has3dModel`)는 `SHOWCASE` 테이블에 비정규화하여 저장한다.
  - 이미지 등록/변경/삭제 시 `primaryImageUrl`을 동기화한다.
  - 3D 모델 생성 완료 시 `has3dModel`을 동기화한다.

### 3-2. 상태 등급 기준

| 등급 | 설명 |
|:----|:-----|
| S | 미착용 / 새 상품 |
| A | 착용감 있으나 상태 우수 (사용감 거의 없음) |
| B | 보통 사용감 (눈에 띄는 사용 흔적 있음) |
| C | 사용감 많음 (기능에는 문제 없음) |

### 3-3. 쇼케이스 상태 전이

```
ACTIVE → HIDDEN    (소유자가 비공개 전환)
HIDDEN → ACTIVE    (소유자가 공개 전환)
ACTIVE → DELETED   (소유자가 삭제)
HIDDEN → DELETED   (소유자가 삭제)
ACTIVE → SOLD      (직거래 완료 시 판매자가 변경 / 안전거래 완료 시 자동 변경)
```

| 상태 | 설명 |
|:----|:-----|
| ACTIVE | 공개 상태, 목록/검색에 노출 |
| HIDDEN | 비공개, 소유자만 조회 가능 |
| SOLD | 판매 완료 (조회는 가능하나 거래/채팅 요청 불가) |
| DELETED | 삭제됨 (소프트 삭제, 복구 불가) |

- `SOLD` 상태의 쇼케이스는 목록에 "판매 완료" 표시와 함께 노출된다.
- `SOLD` 상태에서는 새로운 채팅방 생성 및 거래 요청이 불가하다.
- `DELETED` 상태의 쇼케이스는 모든 API에서 조회되지 않는다.
- 쇼케이스 삭제 시 연관된 댓글도 함께 `DELETED` 처리된다.
- `[TBD]` 진행 중인 거래가 있는 쇼케이스의 삭제 가능 여부

### 3-4. 쇼케이스 수정/삭제 권한

- 쇼케이스의 수정 및 삭제는 소유자(`ownerId`)만 가능하다.

### 3-5. 판매 여부 (`isForSale`)

- `isForSale`이 `true`인 쇼케이스만 거래 요청을 할 수 있다.
- 소유자는 언제든 `isForSale`을 변경할 수 있다.
- `[TBD]` 판매 전환 시 휴대폰 인증 필수 여부

---

## 4. SHOWCASE IMAGE (쇼케이스 이미지)

### 4-1. 이미지 규칙

- 쇼케이스당 최소 1개의 일반 이미지가 존재해야 한다.
- 대표 이미지(`isPrimary = true`)는 반드시 1개만 존재해야 한다.
- 마지막 남은 이미지는 삭제할 수 없다.
- 이미지 정렬 순서(`sortOrder`)는 소유자가 변경할 수 있다.
- `[TBD]` 쇼케이스당 최대 이미지 개수 (예: 10개)
- `[TBD]` 이미지 파일 크기/형식 제한

---

## 5. SHOWCASE 3D MODEL (3D 모델)

### 5-1. 3D 모델 생성 흐름

```
쇼케이스 등록 시 modelSourceImages 첨부
    → REQUESTED (생성 요청)
    → GENERATING (생성 중)
    → COMPLETED (완료) or FAILED (실패)
```

### 5-2. 3D 모델 상태 전이

```
REQUESTED (등록 시)
  ├─→ PREPARING (Worker 가 잡음, Tripo 호출 전)
  │     ├─→ GENERATING + taskId (Tripo 호출 성공)
  │     │     ├─→ COMPLETED (폴링 → 성공)
  │     │     └─→ FAILED (폴링 → 실패 / 타임아웃 15분)
  │     ├─→ FAILED (Tripo Non-retryable: 크레딧 부족, 이미지 거부 등)
  │     ├─→ UNAVAILABLE (Circuit Breaker OPEN)
  │     └─→ REQUESTED (크래시 → Recovery 자동 재시도, retryCount 증가)
  └─→ REQUESTED (Outbox 발행 지연 → Recovery 재등록)

FAILED / UNAVAILABLE → REQUESTED (사용자 재요청)
COMPLETED → (종결, 전이 불가)
```

| 상태 | 설명 |
|:----|:-----|
| REQUESTED | 생성 요청됨, Outbox 에 메시지 쌓여있고 Worker 가 아직 잡지 않은 상태 |
| PREPARING | Worker 가 메시지를 잡고 Tripo 호출을 준비하는 상태. generationTaskId=NULL 보장 (과금 미발생) |
| GENERATING | Tripo task 생성 성공, generationTaskId 존재. 폴링 스케줄러가 상태 확인 중 |
| COMPLETED | 생성 완료, GLB 파일과 프리뷰 이미지가 S3 에 저장됨, 3D 뷰어에서 조회 가능 |
| FAILED | 생성 실패, 사용자 재요청 가능 |
| UNAVAILABLE | 3D 생성 서비스 일시 이용 불가 (Circuit Breaker OPEN), 서비스 복구 후 재요청 가능 |

### 5-3. 3D 모델 생성 규칙

- 소스 이미지는 최소 4장이 필요하다. (앞/뒤/좌/우)
- 이미 생성 중(`PREPARING` 또는 `GENERATING`)인 상태에서는 재요청할 수 없다.
- 하나의 쇼케이스에는 최대 1개의 3D 모델만 존재한다.
- 재요청 시 기존 모델과 소스 이미지는 대체된다.
- 3D 모델 생성 실패 시 자동 재시도 최대 **3회** (PREPARING 상태에서 크래시 복구 시)
- PREPARING 상태 stuck 감지 및 자동 복구: **2분** 초과 시
- REQUESTED 상태 stuck 감지 및 Outbox 재등록: **5분** 초과 시
- `[TBD]` 사용자당 3D 모델 생성 일일 요청 제한

### 5-4. 외부 서비스 (Tripo API)

3D 모델 생성은 [Tripo AI](https://platform.tripo3d.ai) API를 사용한다.

**API 흐름**:
```
1. 소스 이미지 4장을 Tripo에 업로드 → image_token 획득
2. multiview_to_model Task 생성 → task_id 획득
3. Task 상태 폴링 (3초 간격, 최대 5분) → queued → running → success/failed
4. 성공 시 GLB/프리뷰 다운로드 → S3에 영구 저장
```

**Task 생성 파라미터**:

| 파라미터 | 현재 값 | 설명 |
|:--------|:------|:-----|
| `type` | `multiview_to_model` | 다중 이미지 → 3D |
| `model_version` | `v2.5-20250123` | AI 모델 버전 |
| `texture` | 기본값 `true` | 텍스처 생성 여부 |
| `pbr` | 기본값 `true` | PBR 머티리얼 생성 |
| `texture_quality` | 기본값 `standard` | 텍스처 품질 (`standard` / `detailed`) |
| `face_limit` | 미설정 | 최대 폴리곤 수 (모바일 렌더링 시 5000~10000 권장) |

**출력 형식**:

| 필드 | 형식 | 설명 |
|:----|:----|:-----|
| `output.pbr_model` | GLB URL | PBR 모델 (**multiview의 메인 출력**, `output.model`은 null) |
| `output.rendered_image` | WebP URL | 미리보기 이미지 |

> 다운로드 URL은 단시간 내 만료되므로 즉시 S3에 저장해야 한다.

**크레딧 소모**: multiview 1회당 약 30~60 크레딧

**환경변수**: `TRIPO_ENABLED`, `TRIPO_API_KEY`, `TRIPO_BASE_URL`, `TRIPO_POLL_INTERVAL_MS`, `TRIPO_TIMEOUT_MS`, `TRIPO_MODEL_VERSION`

---

## 6. SHOWCASE COMMENT (댓글)

### 6-1. 댓글 규칙

- 인증된 사용자만 댓글을 작성할 수 있다.
- 댓글 내용(`content`)은 필수이며 빈 문자열일 수 없다.
- 댓글의 수정 및 삭제는 작성자(`authorId`)만 가능하다.
- 삭제된 댓글은 소프트 삭제(`DELETED`)로 처리한다.
- `ACTIVE` 상태의 쇼케이스에만 댓글을 작성할 수 있다.

### 6-2. 댓글 상태

| 상태 | 설명 |
|:----|:-----|
| ACTIVE | 정상 노출 |
| DELETED | 삭제됨 (목록에서 제외) |

- `[TBD]` 댓글 글자 수 제한 (예: 최대 500자)
- `[TBD]` 대댓글(계층형 댓글) 지원 여부

---

## 7. CHAT (채팅)

> **설계 근거 (ADR)**:
> - [ADR-005 채팅 프로토콜](../architecture/adr/ADR-005-chat-protocol.md) — WebSocket + STOMP + Kafka + MySQL 선택 이유
> - [ADR-006 Transaction Ticket 패턴](../architecture/adr/ADR-006-transaction-ticket-pattern.md) — 채팅/거래 결합 설계
> - [ADR-007 채팅/거래/결제 BC 경계](../architecture/adr/ADR-007-chat-transaction-payment-boundaries.md) — 단방향 의존 규칙
>
> **리서치**: [2026-04-15 채팅 설계 종합](../research/2026-04-15-chat-design.md) — 국내외 15+ 소스, 당근페이 발표 분석, Phase 로드맵

### 7-1. 기본 원칙

- **1:1 채팅만 지원** (그룹 채팅 없음)
- 채팅방은 **쇼케이스 단위 + 판매자-구매자 쌍**으로 존재한다.
- 채팅방 유니크 키: `(showcase_id, buyer_id)` — 같은 쇼케이스에 같은 구매자가 여러 채팅방을 만들 수 없다. 판매자는 쇼케이스 소유자이므로 고정.
- 채팅에서 합의된 거래는 **§7-6 Transaction Ticket**을 통해서만 실제 거래로 전환된다 (채팅 ↔ 거래 직접 결합 금지).

### 7-2. 채팅방 생성

- 진입점은 **쇼케이스 상세 페이지의 "채팅하기" 버튼 하나**로 단일화된다.
- 구매자가 "채팅하기"를 누르면 다음 중 하나가 일어난다:
  - 기존 채팅방이 있으면 해당 채팅방으로 이동
  - 없으면 새 채팅방을 `ACTIVE` 상태로 생성
- 판매자는 자신의 쇼케이스에 대해 "채팅하기"를 누를 수 없다 (자기 자신과의 채팅 금지).
- `ACTIVE`가 아닌(DELETED/SOLD 외) 쇼케이스에는 신규 채팅방 생성 불가.

### 7-3. 채팅방 상태

| 상태 | 설명 |
|:----|:-----|
| ACTIVE | 정상 대화 가능 |
| CLOSED | 거래 완료(`SOLD`) 또는 쇼케이스 삭제 등으로 닫힘. 과거 메시지는 열람 가능, 신규 메시지 송신 불가 |

- 쇼케이스가 `SOLD` 또는 `DELETED`로 전이되면 해당 쇼케이스의 모든 채팅방은 `CLOSED`로 자동 전환된다.

### 7-4. 메시지 규칙

- 텍스트 메시지 최대 길이: 2,000자
- 메시지 타입 (`message_type`):
  - `TEXT` — 일반 텍스트
  - `IMAGE` — 이미지 (Phase 4에서 도입, MVP 제외)
  - `SYSTEM_TICKET_ISSUED` — 거래 티켓 발급 시스템 메시지
  - `SYSTEM_TRANSACTION_STARTED` — 거래 시작 (티켓 소비됨)
  - `SYSTEM_PAYMENT_COMPLETED` — 결제 완료 (ESCROW)
  - `SYSTEM_TRANSACTION_COMPLETED` — 거래 완료
  - `SYSTEM_TRANSACTION_CANCELLED` — 거래 취소
- 시스템 메시지의 부가 정보(티켓 ID, 거래 ID 등)는 `payload_json` 필드에 담는다.

### 7-5. 읽음 처리 정책

- 읽음 처리 시점: **사용자가 해당 채팅방에 진입한 시점**에 그 시점까지의 메시지를 일괄 읽음 처리
- 각 유저의 읽음 상태는 `CHAT_READ_MARKER` 테이블의 `last_read_message_id`로 관리한다 (메시지 단위 읽음 플래그 없음)
- 미읽음 카운트(unread count) = `COUNT(chat_message WHERE message_id > last_read_message_id AND sender_id != me)`
- 푸시 알림은 읽음 처리와 독립적으로 동작한다 (오프라인 상태에서도 푸시 발송).

### 7-6. Transaction Ticket 패턴

거래는 채팅방 내부에서 **티켓 발급 → 조회 → 사용** 3단계로 진행된다. 티켓이 채팅과 거래의 유일한 계약 지점이며, 채팅방 외 진입점(마이페이지 등) 확장도 동일 메커니즘으로 가능하다.

**전체 흐름**:
```
1. 판매자 또는 구매자가 채팅방에서 "거래 요청" 선택
2. 서버가 Transaction Ticket 발급 (ISSUED)
   - 맥락: SHOWCASE_DIRECT 또는 SHOWCASE_ESCROW
   - 금액·판매자·구매자·유효기간 서버가 확정
3. 채팅방에 SYSTEM_TICKET_ISSUED 메시지 자동 삽입 (payload_json에 ticket_id)
4. 상대방이 티켓 링크(gearshow://transaction/start?ticket=...)로 거래 수락
5. 서버가 티켓을 원자적으로 USED 처리하며 TRANSACTION 생성
6. 채팅방에 SYSTEM_TRANSACTION_STARTED 메시지 자동 삽입
```

**티켓 핵심 규칙**:
- 1회용 (한 번 사용되면 소비됨)
- 유효기간 1시간 (만료 시 EXPIRED)
- 발급자만 CANCELLED 처리 가능 (발급자의 의사 변경 반영)
- 금액은 발급 시점에 서버가 확정 — 클라이언트 조작 불가
- 티켓 상태 전이: `ISSUED → USED` / `ISSUED → EXPIRED` / `ISSUED → CANCELLED`

**티켓 경유의 이점**:
- 채팅방과 거래의 **단방향 의존** (채팅방 → 티켓 발급 → 거래 생성)
- 거래 엔티티는 ticketId만 알면 되고 채팅방을 몰라도 됨
- 추후 "쇼케이스 상세에서 직접 거래 요청" 같은 외부 진입점도 동일 티켓 메커니즘으로 수용 가능
- 딥링크 조작 공격 방지 (티켓 ID 외 정보가 URL에 노출되지 않음)

### 7-7. 메시지 삭제

- 각 유저는 **본인이 보낸 메시지만** 삭제할 수 있다.
- 삭제는 soft delete로 처리된다 (`message_status = DELETED`).
- 삭제된 메시지는 "삭제된 메시지입니다" 플레이스홀더로 표시된다.
- 시스템 메시지는 삭제할 수 없다.

### 7-8. 미결 사항

- `[TBD]` 채팅방당 메시지 보존 기간 (무기한 vs 1년 등)
- `[TBD]` 채팅방 즐겨찾기/고정 기능 도입 여부
- `[TBD]` 차단 기능 (상대방 차단 시 채팅방 처리)
- `[TBD]` 신고 기능 (메시지/채팅방 단위)
- `[TBD]` 타이핑 인디케이터 (현재 MVP 제외)

---

## 8. TRANSACTION (거래)

> **설계 근거 (ADR)**:
> - [ADR-006 Transaction Ticket 패턴](../architecture/adr/ADR-006-transaction-ticket-pattern.md) — `TRANSACTION.chat_room_id` FK 제거 이유, 티켓 경유 원칙
> - [ADR-007 BC 경계](../architecture/adr/ADR-007-chat-transaction-payment-boundaries.md) — transaction 이 ticket 만 참조하는 이유

거래는 **직거래(DIRECT)** 와 **안전거래(ESCROW)** 두 가지 방식으로 나뉘며, 각각 흐름이 다르다.

### 8-1. 공통 규칙

- `isForSale = true`인 쇼케이스만 거래가 가능하다.
- 구매자, 판매자는 휴대폰 인증이 완료된 상태여야 한다.
- `agreedPrice`에는 실제 판매 완료 금액이 기록된다.
- **모든 거래 생성은 `TRANSACTION_TICKET`을 경유한다** (§7-6). 채팅방·쇼케이스 상세 어느 진입점이든 티켓을 발급한 뒤 그 티켓을 소비하여 거래를 만든다.
- 거래 엔티티는 티켓의 맥락(`context_type`, `context_ref_id`)만 참조하며, 채팅방 ID를 직접 의존하지 않는다.

### 8-2. 직거래 (DIRECT)

직거래는 `TRANSACTION` 레코드를 생성하지만, 안전거래처럼 단계별 상태 전이가 이루어지지 않는다. 채팅에서 판매자와 구매자가 자유롭게 협의하고 오프라인에서 거래를 진행한다.

**흐름**:
```
1. 구매자가 채팅방에서 구매 의향 전달
2. 판매자가 isForSale = true로 변경 (이미 true라면 생략)
3. 채팅에서 가격·장소·일정 협의
4. 일방이 "직거래 약속" 버튼 선택 → 서버가 DIRECT 컨텍스트 티켓 발급
   → SYSTEM_TICKET_ISSUED 메시지가 채팅방에 삽입됨
5. 상대방이 티켓 수락 → 서버가 티켓을 USED 처리하며 TRANSACTION을 COMPLETED로 생성
   → SYSTEM_TRANSACTION_COMPLETED 메시지 삽입
6. 오프라인에서 실제 물품·대금 전달
7. 판매자가 쇼케이스를 SOLD로 변경하여 확정
```

- 판매자만 쇼케이스를 `SOLD` 상태로 변경할 수 있다.
- 직거래 `TRANSACTION`은 상태 전이 없이 완료 시점에 `COMPLETED`로 바로 생성된다.
- `agreedPrice`에 실제 거래 금액을 기록한다.
- 상태 전이: `ACTIVE → SOLD`
- `SOLD` 변경 시 `isForSale`은 자동으로 `false`로 변경된다.

### 8-3. 안전거래 (ESCROW)

안전거래는 `TRANSACTION` + `PAYMENT` 레코드를 통해 플랫폼이 거래를 중개한다. 채팅방 내 안전거래 버튼을 통해 시작된다.

**흐름**:
```
1. 채팅에서 가격 합의
2. 판매자 또는 구매자가 채팅방에서 "안전거래" 선택
   → 서버가 SHOWCASE_ESCROW 컨텍스트 티켓 발급 (ISSUED, 1시간 유효)
   → SYSTEM_TICKET_ISSUED 메시지 채팅방에 삽입
3. 상대방이 티켓 수락
   → 서버가 티켓 USED 처리하며 TRANSACTION을 PENDING 상태로 생성
   → SYSTEM_TRANSACTION_STARTED 메시지 삽입
4. 구매자가 결제 진행 (PENDING → IN_PROGRESS)
   → SYSTEM_PAYMENT_COMPLETED 메시지 삽입
5. 판매자가 상품 발송/전달
6. 구매자가 수령 확인 또는 자동 정산 → 거래 완료 (COMPLETED)
   → SYSTEM_TRANSACTION_COMPLETED 메시지 삽입
```

#### 8-3-1. 거래 상태 전이

```
PENDING → IN_PROGRESS → COMPLETED
                      → CANCELLED
PENDING → CANCELLED
```

| 상태 | 설명 |
|:----|:-----|
| PENDING | 거래 생성됨, 구매자 결제 대기 |
| IN_PROGRESS | 결제 완료, 거래 진행 중 |
| COMPLETED | 거래 완료 (정산 처리) |
| CANCELLED | 거래 취소 |

#### 8-3-2. 거래 상태별 행위 규칙

| 행위 | 허용 주체 | 허용 상태 |
|:----|:---------|:---------|
| 안전거래 요청 (거래 생성) | 구매자 또는 판매자 | - |
| 결제 (PENDING → IN_PROGRESS) | 구매자 | PENDING |
| 수령 확인 (IN_PROGRESS → COMPLETED) | 구매자 | IN_PROGRESS |
| 거래 취소 | 판매자 또는 구매자 | PENDING, IN_PROGRESS |

- 동일 채팅방에서 진행 중인 거래(`PENDING`, `IN_PROGRESS`)가 있으면 새 거래를 생성할 수 없다.

### 8-4. 거래 완료 후처리 (안전거래)

- 거래가 `COMPLETED`되면 쇼케이스 상태가 `SOLD`로 변경된다.
- `isForSale`은 자동으로 `false`로 변경된다.
- 해당 쇼케이스의 다른 `PENDING` 상태 거래는 자동 `CANCELLED` 처리된다.
- `[TBD]` 거래 완료 후 리뷰/평점 시스템 도입 여부

### 8-5. 거래 취소 규칙 (안전거래)

- `PENDING` 상태: 결제 전이므로 즉시 취소 가능
- `IN_PROGRESS` 상태: 결제 환불 처리가 선행되어야 한다.
- `[TBD]` 취소 사유 입력 필수 여부
- `[TBD]` 취소 페널티 정책 (예: 반복 취소 시 제재)

---

## 9. PAYMENT (결제)

> 결제는 안전거래(ESCROW)에서만 발생한다. 직거래(DIRECT)는 결제 시스템을 사용하지 않는다.

### 9-1. 결제 규칙

- 결제 금액(`amount`)은 합의 가격(`agreedPrice`)과 일치해야 한다.
- 결제 완료 시 거래 상태가 `PENDING → IN_PROGRESS`로 전이된다.

### 9-2. 결제 상태 전이

```
PENDING → PAID → CANCELLED (환불)
PENDING → FAILED
```

| 상태 | 설명 |
|:----|:-----|
| PENDING | 결제 대기 중 |
| PAID | 결제 완료 |
| CANCELLED | 결제 취소 (환불 완료) |
| FAILED | 결제 실패 |

### 9-3. 결제 제공자

- 지원 제공자: `TOSS`, `KAKAO`
- 결제 수단: `CARD`, `BANK_TRANSFER`
- `[TBD]` 에스크로 보관 기간 및 자동 정산 조건

---

## 10. 크로스 도메인 규칙

### 10-1. 도메인 간 영향 관계

| 트리거 | 영향 | 설명 |
|:------|:-----|:-----|
| 사용자 탈퇴 | SHOWCASE → DELETED | 소유 쇼케이스 전체 삭제 |
| 사용자 탈퇴 | CHAT_ROOM → CLOSED | 참여 채팅방 종료 |
| 사용자 정지 | SHOWCASE → HIDDEN | 소유 쇼케이스 비공개 전환 |
| 쇼케이스 삭제 | COMMENT → DELETED | 연관 댓글 전체 삭제 |
| 직거래 완료 | SHOWCASE → SOLD | 판매자가 수동으로 판매 완료 처리 |
| 안전거래 완료 | SHOWCASE → SOLD | 자동으로 판매 완료 처리 |
| 판매 완료 (SOLD) | SHOWCASE.isForSale → false | 자동으로 판매 종료 |
| 안전거래 완료 | 다른 PENDING 거래 → CANCELLED | 동일 쇼케이스의 다른 대기 거래 취소 |

### 10-2. 불변 규칙 (Invariants)

- `SOLD` 상태의 쇼케이스에는 새로운 거래 요청 및 채팅방 생성이 불가하다.
- `WITHDRAWN` 상태의 사용자는 어떤 쓰기 작업도 수행할 수 없다.
- `SUSPENDED` 상태의 사용자는 로그인 및 쓰기 작업이 불가하다.
- `isForSale = true`인 쇼케이스만 거래가 가능하다.

---

## [TBD] 미확정 항목 요약

| 항목 | 섹션 | 설명 |
|:----|:-----|:-----|
| 일일 인증 요청 횟수 제한 | 1-3 | 휴대폰 인증 요청 제한 |
| 탈퇴 후 개인정보 보관 기간 | 1-4 | 유예 기간 및 영구 삭제 시점 |
| 카탈로그 등록 권한 | 2-3 | 관리자 전용 vs 사용자 제안 방식 |
| 진행 중 거래 시 쇼케이스 삭제 | 3-3 | 삭제 차단 vs 거래 자동 취소 |
| 판매 전환 시 휴대폰 인증 필수 | 3-5 | isForSale 변경 조건 |
| 이미지 개수/크기 제한 | 4-1 | 최대 이미지 수, 파일 크기 |
| 3D 모델 재시도/일일 제한 | 5-3 | 자동 재시도, 요청 제한 |
| 댓글 글자 수 제한 | 6-2 | 최대 글자 수 |
| 대댓글 지원 여부 | 6-2 | 계층형 댓글 구조 |
| 채팅방 나가기 기능 | 7-2 | 사용자 자발적 종료 |
| 리뷰/평점 시스템 | 8-5 | 거래 완료 후 피드백 |
| 취소 사유/페널티 | 8-6 | 취소 정책 |
| 에스크로 정산 조건 | 9-3 | 보관 기간 및 자동 정산 |
