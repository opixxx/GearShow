# ADR-005: 채팅 프로토콜 — WebSocket + STOMP + Kafka

- **날짜**: 2026-04-15
- **상태**: Accepted
- **영역**: 채팅
- **관련**: ADR-006 (Transaction Ticket), ADR-007 (BC 경계)

## 결정

GearShow 1:1 채팅은 다음 조합으로 구현한다.

- **클라이언트 ↔ 서버**: WebSocket + STOMP (SockJS fallback)
- **서버 간 메시지 전파**: Kafka (파티션 키 = `chatRoomId`)
- **메시지 영속 저장**: MySQL (기존 인프라 재사용)
- **읽음 상태**: `CHAT_READ_MARKER` 테이블, per user `lastReadMessageId`
- **오프라인 푸시**: FCM / APNs (Outbox 경유, Phase 3)

## 배경

채팅 기능 구현은 다음 축에서 선택이 필요했다.

1. **실시간 프로토콜**: WebSocket / SSE / Long Polling
2. **서버 간 브로드캐스트**: Redis Pub/Sub / Kafka / RabbitMQ / 없음(단일 서버)
3. **메시지 저장소**: MySQL / DynamoDB / Cassandra / MongoDB / Redis
4. **읽음 처리 구현**: 메시지별 read-flag / user별 last-read-id

프로젝트 제약:
- **현재 단일 EC2 t3.small**, 트래픽 초기 수준
- **이미 Kafka/MySQL 보유** (Outbox 패턴, 헥사고날 경험)
- **Redis 미도입** (docker-compose에 없음)
- **Flutter 클라이언트** (WebSocket 네이티브 지원)
- 단독 1인 개발 — 운영 복잡도 관리 중요

## 대안과 기각 사유

### 프로토콜

| 대안 | 기각 사유 |
|---|---|
| SSE | 서버 → 클라이언트 단방향. 채팅은 양방향 필요 |
| Long Polling | 매 요청마다 인증 헤더 재전송, 서버 스레드 점유 과다. 레거시 fallback용 |
| Raw WebSocket (STOMP 없이) | 라우팅·구독을 직접 구현해야 함. Spring 생태계 표준 상실 |
| gRPC bidi streaming | 브라우저 네이티브 지원 약함, 운영 도구 부족 |

### 서버 간 브로드캐스트

| 대안 | 기각 사유 |
|---|---|
| Redis Pub/Sub | **구독자 없으면 메시지 유실** (휘발성). 별도 인프라 추가 부담. 오프라인 유저 메시지 처리에 또 다른 저장소 필요 |
| RabbitMQ | Kafka 이미 있는데 별도 브로커 추가 = 운영 복잡도 2배 |
| 단일 서버 유지 (브로커 없음) | 수평 확장 불가. 서버 재시작 시 모든 연결 끊김. 초기엔 가능하나 마이그레이션 비용 큼 |

**Kafka 선택 근거**:
- 이미 운영 중 (Outbox Relay, 재설계 경험)
- 파티션 키(`chatRoomId`)로 **같은 방 메시지 순서 보장 + 룸 단위 병렬** 자연 확보
- 오프라인 유저는 consumer offset으로 자연 처리 (별도 저장소 불필요)
- min.insync.replicas 한계는 MSK 이관 시 해결 (Kafka 재설계 문서 있음)

### 메시지 저장소

| 대안 | 기각 사유 |
|---|---|
| DynamoDB / Cassandra / ScyllaDB | 당근·Discord 수준 트래픽 아님. 운영 부담·학습곡선·비용 모두 과잉 |
| MongoDB | 단일 저장소 다양성 확보 비용(운영 툴·백업·JPA 포기) > 이점 |
| Redis (메인 저장소) | 메모리 비용·영속성 한계 |
| S3 (로그 아카이브) | 조회 쿼리 불편. 장기 보존용으론 가능 (미래 과제) |

**MySQL 선택 근거**:
- 스키마 이미 설계됨 (`CHAT_ROOM`, `CHAT_MESSAGE`)
- 헥사고날 Aggregate 패턴 유지 가능
- 규모 한계 도달 시 **Aggregate 경계만 잘 지켜두면 DB 교체 가능**
- 운영 인프라 공유 (Backup, Monitoring)

### 읽음 처리

| 대안 | 기각 사유 |
|---|---|
| 메시지별 `isRead` 플래그 | 1:N 확장 시 N·M 레코드 증가. 현재 스키마도 이 방식인데 비효율 |
| 메시지별 read 서브테이블 `(messageId, userId)` | N·M 증가 동일 |
| Redis Sorted Set | 영속성 부재, 별도 인프라 추가 |

**`CHAT_READ_MARKER` 선택 근거**:
- 유저당 채팅방당 레코드 1개 — O(1) 증가
- `UPDATE ... SET last_read_message_id=? WHERE user_id=? AND chat_room_id=?` 로 동시성 자연 처리 (row lock)
- 미읽음 수 = `COUNT(WHERE id > last_read_id AND sender != me)` — 인덱스 활용

## 아키텍처 요약

```
Flutter Client
    │ WebSocket (STOMP)
    ▼
ALB (sticky session cookie, 1시간)
    │
    ├─► Backend-1 :8080 (WebSocket 세션 보유)
    └─► Backend-2 :8080 (WebSocket 세션 보유)
           │       │
           │       │ publish / consume
           ▼       ▼
          Kafka (topic: chat.messages, partition key: chatRoomId)
           │
           ▼
        MySQL (chat_room, chat_message, chat_read_marker)

오프라인 푸시 (Phase 3):
Backend → outbox_message → Kafka → Push Relay → FCM/APNs
```

## 트레이드오프

### 얻은 것
- Spring 생태계 표준 도구로 빠른 구현
- 인프라 추가 없이 기존 자산 재활용 (Kafka, MySQL)
- 명확한 확장 경로 (수평 확장 시 Kafka가 이미 준비됨)
- `CHAT_READ_MARKER` 로 unread 계산 효율

### 포기한 것
- **극한 규모 대응** — 트래픽 수백만 건/일 수준이면 DynamoDB+Scylla 조합이 더 적합. 현재 스케일에선 과잉
- **Redis 활용 시나리오** — 온라인 presence, typing 인디케이터 등 일부 기능은 Redis가 자연스러운데, Redis 미도입 방침으로 구현 복잡도 약간 증가 (MVP 제외됨)
- **WebSocket 분산 상태 관리** — 현재는 sticky session으로 단순화. 진짜 분산 환경에선 consistent hashing + 세션 레지스트리 필요 (미래 과제)
- **메시지 순서 분산 보장** — 단일 파티션 = 단일 consumer. 특정 방에 극한 트래픽 시 병목 가능 (1:1 채팅이라 가능성 낮음)

### 미래에 재검토할 시점
- 일 활성 채팅방 10만 이상
- 단일 채팅방 동시 접속 100+ (1:1 아닌 확장 시)
- MySQL 채팅 테이블이 1억 행 접근
- Kafka 지연이 500ms 이상 평상시

## 영향 범위

- `docs/diagram/schema.md` — CHAT_MESSAGE 에 `seq`, `payload_json`, CHAT_READ_MARKER 신규
- `docs/business/biz-logic.md §7` — 채팅 규칙
- `docs/spec/api-spec.md §8` — WebSocket 엔드포인트 정의
- `backend/build.gradle` — `spring-boot-starter-websocket` 의존성 추가 예정

## 참고 자료

- Slack Architecture — 대형 서비스의 WebSocket + Vitess (MySQL 샤딩) 패턴
- 당근 2200만 채팅 시스템 — Go + DynamoDB 는 규모 도달 후 전환 사례
- LINE LIVE — Actor 모델 + Redis Pub/Sub 은 초대용량 실시간 케이스
- 여러 한국 블로그에서 **Redis Pub/Sub → Kafka 전환 회고** 반복 관찰 (휘발성이 운영 장애 유발)
