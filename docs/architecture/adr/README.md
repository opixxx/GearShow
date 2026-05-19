# Architecture Decision Records (ADR)

프로젝트의 중요한 아키텍처 결정과 그 근거를 시간순으로 기록한다.
각 ADR은 **결정 / 배경 / 대안 / 트레이드오프** 4가지를 담는다.

## 작성 원칙

- **번호는 불변**: 한번 부여된 번호는 변경하지 않는다. 결정이 번복되면 새 ADR로 기록하고 기존 ADR의 상태를 `Superseded by ADR-XXX`로 변경.
- **되돌릴 수 없는 결정만**: 나중에 바꾸기 어려운 결정(기술 스택, 데이터 모델, 프로토콜)만 ADR로 기록.
- **왜**에 집중: 무엇을 결정했는지보다 왜 그 선택인지가 핵심.
- **대안을 기록**: 고려했던 다른 선택지와 그걸 안 고른 이유를 남겨야 미래에 재검토할 근거가 된다.

## 상태 (Status)

- `Proposed` — 검토 중
- `Accepted` — 채택됨 (기본)
- `Superseded by ADR-XXX` — 다른 ADR로 대체됨
- `Deprecated` — 더 이상 유효하지 않으나 히스토리 보존

## 인덱스

| 번호 | 제목 | 상태 | 영역 |
|:----:|:-----|:----:|:----:|
| [ADR-005](./ADR-005-chat-protocol.md) | 채팅 프로토콜 — WebSocket + STOMP + Kafka | Accepted | 채팅 |
| [ADR-006](./ADR-006-transaction-ticket-pattern.md) | Transaction Ticket 패턴 채택 | Accepted | 거래 |
| [ADR-007](./ADR-007-chat-transaction-payment-boundaries.md) | 채팅/거래/결제 Bounded Context 경계 | Accepted | 아키텍처 |
| [ADR-008](./ADR-008-chat-jwt-verification-port.md) | 채팅 JWT 검증 포트 분리 | Accepted | 채팅 |
| [ADR-009](./ADR-009-chat-broadcast-port.md) | 채팅 브로드캐스트 — Outbound Port + AFTER_COMMIT 리스너 | Accepted | 채팅 |
| [ADR-010](./ADR-010-3d-pipeline-table-split.md) | 3D 파이프라인 — 도메인/프로세스 테이블 분리 | Accepted | 3D 생성 |
| [ADR-011](./ADR-011-3d-pipeline-multilayer-idempotency.md) | 3D 파이프라인 — 다층 멱등성 전략 | Accepted | 3D 생성 |
| [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md) | 3D 파이프라인 — 선택적 분산 락 + 조건부 UPDATE 동시성 | Accepted | 3D 생성 |
| [ADR-018](./ADR-018-showcase-search-text-composition.md) | Showcase 검색 텍스트 합성 정책 | Superseded by ADR-024 | 검색 |
| [ADR-019](./ADR-019-showcase-search-keyword-policy.md) | Showcase 검색 키워드 정책 (`?keyword=` API) | Superseded by ADR-024 | 검색 |
| [ADR-024](./ADR-024-showcase-search-simplification.md) | Showcase 검색 단순화 — title/description 직접 검색 | Accepted | 검색 |
| [ADR-025](./ADR-025-3d-generation-admission-queue.md) | 3D 생성 동시 작업 입장 큐 — 재발행 방식 (PR #95 inline-drain 폐기) | Accepted | 3D 생성 |
| [ADR-026](./ADR-026-3d-preparing-retryable-compensation.md) | 3D 파이프라인 — PREPARING retryable 실패 보상 전이 (PREPARING→REQUESTED, C1 수정) | Accepted | 3D 생성 |
| [ADR-027](./ADR-027-3d-worker-process-split.md) | 3D Worker 프로세스 분리 — 단일 아티팩트 + 프로파일 역할 분기 | Accepted | 3D 생성 |

> ADR-001 ~ ADR-004 는 향후 기존 주요 결정(헥사고날 채택, Kafka EDA, Tripo 선택 등)을 역추적해 채울 예정.
> ADR-013 ~ ADR-017, ADR-020 ~ ADR-023 는 인덱스 누락 상태 — 별도 docs cleanup PR 에서 일괄 채울 예정.
