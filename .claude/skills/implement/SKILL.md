---
name: implement
description: >
  API 스펙 문서 기반으로 코드를 구현한다. CLAUDE.md 컨벤션에 따라
  domain → application → adapter 순서로 전체 스택을 구현하고
  컴파일 검증 후 리뷰 agent를 호출한다.
user_invocable: true
---

# API 스펙 기반 코드 구현

API 스펙 문서(`docs/spec/api-spec.md`)와 관련 문서를 읽고, CLAUDE.md 컨벤션에 따라 코드를 구현한다.

## 입력

`$ARGUMENTS` = 구현할 API 식별자 (예: `쇼케이스 수정`, `4-4`, `댓글 작성`)

- 인자가 없으면 사용자에게 어떤 API를 구현할지 질문한다.
- API 스펙 번호(예: `4-4`) 또는 기능명(예: `쇼케이스 수정`) 모두 허용한다.

---

## Phase 1: 탐색 (읽기만 수행)

### 1-1. 문서 읽기

아래 문서를 순서대로 Read한다:

1. `docs/spec/api-spec.md` — 구현 대상 API의 엔드포인트, Request/Response, 에러 코드 파악
2. `docs/diagram/schema.md` — 관련 테이블 구조, Aggregate 경계, FK 전략 파악
3. `docs/business/biz-logic.md` — 관련 비즈니스 규칙 확인
4. `docs/spec/architecture-pattern.md` — 계층별 구현 규칙 확인
5. `docs/spec/coding-convention.md` — 네이밍, DTO, 예외 규칙 확인

### 1-2. 기존 코드 파악

구현 대상 도메인의 기존 코드를 확인한다:

```bash
# 대상 도메인의 기존 파일 구조 확인
find backend/src/main/java/com/gearshow/backend/{도메인}/ -name "*.java" | head -30
```

- 이미 존재하는 Domain Model, Port, Service, Adapter를 파악한다.
- 재사용 가능한 클래스가 있는지 확인한다 (중복 생성 금지).

### 1-3. 구현 계획 수립

파악한 내용을 바탕으로 **구현 계획**을 사용자에게 제시한다:

```
## 구현 계획

### 대상 API
- [API 번호] API 이름 (HTTP Method + Path)

### 생성할 파일
1. domain/ — (새로 생성하거나 수정할 파일 목록)
2. application/ — (UseCase, Service, Command/Result, Port)
3. adapter/ — (Controller, Request/Response, PersistenceAdapter)

### 수정할 기존 파일
- (이미 존재하는 파일 중 변경이 필요한 것)

### 의존하는 기존 코드
- (재사용할 Port, Domain Model 등)
```

사용자가 승인하면 Phase 2로 진행한다.

---

## Phase 2: 구현

CLAUDE.md의 코딩 컨벤션을 따라 **컴파일 의존 순서**대로 구현한다.

### 2-1. Domain 계층

순수 비즈니스 로직. Spring/JPA 의존 금지 (Lombok `@Getter`, `@Builder`만 허용).

구현 순서:
1. **VO** (Value Object) — 필요한 경우
2. **Domain Model** — 정적 팩토리 메서드 + Builder, validate 포함
3. **Domain Exception** — ErrorCode enum 경유 필수
4. **Repository Interface** — 도메인 관점의 저장소 인터페이스
5. **Policy** — 여러 엔티티에 걸친 검증 로직 (필요한 경우)

### 2-2. Application 계층

유스케이스 구현. `@Service`, `@Transactional` 허용.

구현 순서:
1. **Command / Result** — record 타입 DTO
2. **Inbound Port** — `{Action}UseCase` 인터페이스
3. **Outbound Port** — `{Entity}Port` 인터페이스
4. **Service** — UseCase 구현체 (1 UseCase = 1 Service, 오케스트레이션만)

### 2-3. Adapter 계층

외부 연동 구현. 모든 프레임워크 어노테이션 허용.

구현 순서:
1. **JPA Entity** — `{Entity}JpaEntity`, Domain Model과 분리
2. **Mapper** — Domain Model ↔ JPA Entity 변환
3. **JPA Repository** — Spring Data JPA 인터페이스
4. **Persistence Adapter** — Outbound Port 구현체
5. **Request / Response DTO** — record 타입, Bean Validation 한글 메시지
6. **Controller** — `@Valid` 필수, Request → Command 변환, Response.from(Result) 변환

### 2-4. 논리 단위별 컴파일 검증

각 계층 구현 완료 시마다 컴파일을 검증한다:

```bash
cd backend && ./gradlew compileJava
```

컴파일 실패 시 즉시 수정한다.

### 2-5. ErrorCode 등록

새로운 에러 케이스가 있으면 `ErrorCode` enum에 추가한다:

- API 스펙의 에러 코드 목록 참조
- 메시지는 한글 필수
- 패턴: `{DOMAIN}_{REASON}` (예: `SHOWCASE_NOT_FOUND`)

---

## Phase 3: 빌드 검증

전체 빌드를 실행하여 기존 테스트를 깨뜨리지 않는지 확인한다:

```bash
cd backend && ./gradlew build
```

- 빌드 실패 시 원인을 분석하고 수정한다.
- 기존 테스트가 깨지면 변경 사항과의 호환성을 확인하고 수정한다.

---

## Phase 4: 리뷰 + 테스트 (병렬)

아래 4개의 subagent를 **동시에 병렬로** 실행한다:

| Agent | 역할 |
|:------|:----|
| **architecture-reviewer** | 의존 방향, BC 격리, Aggregate 설계, 포트/어댑터 검증 |
| **code-reviewer** | SOLID, OOP 설계, 네이밍, DTO/예외 규칙, 보안 검증 |
| **database-optimizer** | N+1 쿼리, 인덱스 전략, 트랜잭션 범위, 쿼리 최적화 |
| **test-writer** | Cucumber 인수 테스트 + 통합 테스트 + 단위 테스트 작성 |

각 agent에게 전달할 프롬프트에는 다음 정보를 포함한다:
- 구현 대상 API 정보
- Phase 2에서 생성/수정한 파일 목록

### 결과 종합

4개 agent의 결과를 종합하여 사용자에게 보고한다:

```
## 리뷰 + 테스트 결과 요약

### architecture-reviewer
- CRITICAL: N건 / MAJOR: N건 / MINOR: N건

### code-reviewer
- CRITICAL: N건 / MAJOR: N건 / MINOR: N건

### database-optimizer
- CRITICAL: N건 / MAJOR: N건 / MINOR: N건

### test-writer
- Cucumber 시나리오: N개 / 통합 테스트: N개 / 단위 테스트: N개
- ./gradlew build: 성공 / 실패

### 조치 필요 사항
- (CRITICAL/MAJOR 이슈 목록)
```

- **리뷰 CRITICAL 0건 + 테스트 빌드 성공**이면 구현 완료를 선언한다.
- **CRITICAL 1건 이상 또는 빌드 실패**이면 수정 후 Phase 3부터 다시 수행한다.

---

## 체크리스트

구현 완료 전 확인 사항:

- [ ] Domain 계층에 Spring/JPA 의존 없음
- [ ] 모든 DTO가 record 타입
- [ ] Bean Validation 메시지 한글
- [ ] ErrorCode enum 경유한 예외 처리
- [ ] `@RequiredArgsConstructor` + `private final` 의존성 주입
- [ ] Controller에서 `@Valid` 사용
- [ ] Entity를 Controller에서 직접 반환하지 않음
- [ ] DTO 흐름: Request → Command → Service → Result → Response
- [ ] 메서드 20줄 이내, 단일 책임
- [ ] 주석, 로그, 예외 메시지 한글
- [ ] `./gradlew build` 통과
