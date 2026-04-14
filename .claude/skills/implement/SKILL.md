---
name: implement
description: >
  **[오케스트레이터 내부 도구]** GearShow Backend의 헥사고날 구현 순서(domain → application → adapter)와
  각 계층별 체크리스트를 제공한다. 사용자가 직접 호출하지 않으며, `orchestrator` 스킬의 Phase 2
  (Worktree 구현) 단계에서 내부적으로 참조·호출된다.
  Intake/EXEC_PLAN/검증/PR 단계는 이 스킬이 담당하지 않는다 — 오케스트레이터가 주도한다.
user_invocable: false
---

# [Internal] 헥사고날 구현 순서 가이드

> ⚠️ **이 스킬은 `orchestrator` Phase 2 내부 호출 전용이다.**
> 사용자가 직접 호출하면 worktree·EXEC_PLAN·자가수정 루프가 우회된다.
> 직접 구현 요청은 `orchestrator`로 라우팅한다.

이 스킬은 **오케스트레이터 Phase 2**에서만 참조된다.
오케스트레이터가 Intake(Phase 0) · EXEC_PLAN(Phase 1) · 검증(Phase 3·4) · 커밋/PR(Phase 5)을 이미 처리한 상태에서,
**linked worktree 내부의 실제 코드 작성 순서와 체크리스트**만 제공한다.

---

## 전제 조건

호출 시점에 다음이 모두 만족되어야 한다:

- [ ] linked worktree 내부에서 실행 중 (`.git`이 파일)
- [ ] EXEC_PLAN.md의 `<TODO:>` 마커가 모두 채워짐
- [ ] 변경 대상(Affected) 섹션에 domain/application/adapter 계층별 파일 목록이 명시됨
- [ ] 위험도(Risk) 등급이 결정됨

위 조건이 하나라도 미충족이면 오케스트레이터 Phase 0·1로 되돌아간다.

---

## 컴파일 의존 순서대로 구현

도메인 → 애플리케이션 → 어댑터 순서로 내려간다.
각 계층 완료 시마다 `./gradlew compileJava --quiet` 로 컴파일 검증하고, 실패 시 즉시 수정한다.

### 1. Domain 계층

순수 비즈니스 로직. Spring·JPA 의존 금지 (Lombok `@Getter`, `@Builder`만 허용).
ArchUnit이 위반 시 커밋·Stop 훅에서 자동 차단한다.

구현 순서:
1. **VO** (Value Object) — 필요한 경우. 불변, 의미 있는 원시 타입 캡슐화.
2. **Domain Model** — 정적 팩토리 메서드(`create`, `of`) + Builder, 검증 포함.
3. **Domain Exception** — `ErrorCode` enum 경유 필수 (상세: `orchestrator/references/exception-rules.md`).
4. **Repository Interface** — 도메인 관점의 저장소 인터페이스 (DB 기술 용어 금지).
5. **Policy** — 여러 엔티티에 걸친 검증 로직 (필요한 경우).

체크:
- `final` 필드 우선, setter 지양
- null 주입 금지 — 필수 필드는 반드시 값 제공
- 상태 전이는 도메인 메서드(`activate()`, `cancel()`)로 캡슐화

### 2. Application 계층

유스케이스 구현. `@Service`, `@Transactional` 허용.

구현 순서:
1. **Command / Result** — `record` 타입 DTO.
2. **Inbound Port** — `{Action}UseCase` 인터페이스.
3. **Outbound Port** — `{Entity}Port` / `{Service}Client` 인터페이스.
4. **Service** — UseCase 구현체. **1 UseCase = 1 Service**. 오케스트레이션만 수행하고 비즈니스 로직은 도메인에 위임.

체크:
- `@RequiredArgsConstructor` + `private final` 주입
- `@Transactional` 위치 확인 (외부 API 호출은 트랜잭션 밖)
- 읽기 전용은 `@Transactional(readOnly = true)`

### 3. Adapter 계층

외부 연동 구현. 모든 프레임워크 어노테이션 허용.

구현 순서:
1. **JPA Entity** — `{Entity}JpaEntity`, 도메인 모델과 분리.
2. **Mapper** — Domain Model ↔ JPA Entity 변환.
3. **JPA Repository** — Spring Data JPA 인터페이스.
4. **Persistence Adapter** — Outbound Port 구현체.
5. **Request / Response DTO** — `record` 타입, Bean Validation **한글 메시지**.
6. **Controller** — `@Valid` 필수, Request → Command 변환, Response.from(Result) 변환.

체크:
- Entity를 Controller에서 직접 반환 금지
- 모든 Request DTO에 Bean Validation 적용 (`@NotBlank`, `@Size`, `@URL` 등)
- Bean Validation 메시지 한글

---

## ErrorCode 등록

새로운 에러 케이스가 있으면 `ErrorCode` enum에 추가:

- API 스펙의 에러 코드 목록 참조
- 메시지는 한글 필수
- 네이밍 : `{DOMAIN}_{REASON}` (예: `SHOWCASE_NOT_FOUND`)

상세 규칙: `orchestrator/references/exception-rules.md`

---

## 계층별 체크리스트 (구현 직후 점검)

- [ ] Domain 계층에 Spring/JPA 의존 없음 (ArchUnit이 검증)
- [ ] 모든 DTO가 `record` 타입
- [ ] Bean Validation 메시지 한글
- [ ] ErrorCode enum 경유한 예외 처리
- [ ] `@RequiredArgsConstructor` + `private final` 의존성 주입
- [ ] Controller에서 `@Valid` 사용
- [ ] Entity를 Controller에서 직접 반환하지 않음
- [ ] DTO 흐름: Request → Command → Service → Result → Response
- [ ] 메서드 20줄 이내, 단일 책임
- [ ] 주석·로그·예외 메시지 한글
- [ ] 각 계층 완료 시 `./gradlew compileJava` 성공

---

## 이 스킬이 하지 않는 것 (오케스트레이터 담당)

| 단계 | 담당 | 참조 |
|---|---|---|
| Intake (요구 확정·질문) | orchestrator Phase 0 | `orchestrator/SKILL.md` |
| EXEC_PLAN 생성 | orchestrator Phase 1 | `scripts/start-task.sh` |
| 계산적 검증 (compile·archTest) | orchestrator Phase 3 | `tools/hooks/verify-and-block.sh` |
| 추론적 검증 (4 agents 병렬) | orchestrator Phase 4 | 4개 서브에이전트 |
| 커밋 + PR | orchestrator Phase 5 | `/pr-guide` |
| Post-merge 트레젝토리 | orchestrator Phase 6 | `docs/agent/trajectories/` |

---

## 참조

- 오케스트레이터 워크플로 : `.claude/skills/orchestrator/SKILL.md`
- 코딩 컨벤션 : `.claude/skills/orchestrator/references/coding-conventions.md`
- 예외 규칙 : `.claude/skills/orchestrator/references/exception-rules.md`
- 테스트 규칙 : `.claude/skills/orchestrator/references/test-rules.md`
- API 명세 : `docs/spec/api-spec.md`
- ERD : `docs/diagram/schema.md`
