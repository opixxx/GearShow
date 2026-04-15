---
name: orchestrator
description: "GearShow Backend 구현/버그수정/리팩토링 작업의 표준 파이프라인(0~6단계)을 강제하는 오케스트레이터. (1) '구현', '추가', '만들어', '엔드포인트', '기능' 등 구현성 요청, (2) '버그', '고쳐', 'fix' 등 수정 요청, (3) '리팩토링', '개선' 요청, (4) feature/fix/refactor 브랜치 작업, (5) worktree·EXEC_PLAN 필요 작업, (6) 여러 계층(domain/application/adapter)에 걸친 변경에 반드시 사용. 단순 질문·문서 조회는 제외."
---

# GearShow Backend — Implementation Orchestrator

모든 구현·버그수정·리팩토링 작업은 이 오케스트레이터의 0~6단계 파이프라인을 따른다.
단계 건너뛰기 금지, 예외 경우 명시적 확인 후 진행.

## 트리거 조건

다음 중 하나라도 해당하면 이 오케스트레이터를 사용한다:

- 소스 코드 파일(`.java`, `.gradle`, `.yml`, `.properties`, `.sql`) 변경이 예상됨
- "구현", "만들어", "추가", "수정", "고쳐", "리팩토링", "버그" 등 작업 키워드 포함
- 새 유스케이스, API 엔드포인트, 이벤트 발행/구독 추가
- Bounded Context 경계를 넘는 변경

**사용하지 않는 경우**: 단순 코드 설명, 문서 조회, 상태 확인, 로그 분석.

---

## 표준 파이프라인 (0 → 6)

```
0. Intake       → 요구 파싱 · 모호점 질문 · 범위 확정
1. EXEC_PLAN    → 구현 단계 + 테스트 케이스 + 위험도 등급
2. Worktree 구현 → linked worktree 내부에서 구현 + 테스트 병행
3. 검증-a (계산적) → compile / test / ArchUnit
4. 검증-b (추론적) → code-reviewer / architecture-reviewer / database-optimizer
5. 커밋 + PR     → 위험도 기반 머지 정책
6. Post-merge   → 트레젝토리 기록 · 학습 루프
```

단계 간 실패 시 복귀 경로:
- 3단계 실패 → 2단계 복귀 (자가수정 루프, 최대 3회 — `references/self-heal-loop.md`)
- 4단계 Critical → 2단계 복귀
- 4단계 Nit → 기록만, 진행
- 에스컬레이션 조건 충족 시 즉시 정지 (`references/escalation.md`)

---

## 0. Intake (강제 질문 지점)

### 0.1 관련 문서 수집 (Intake 질문 이전 필수)

사용자에게 질문하기 **전에**, 이미 결정된 내용을 재질문하지 않도록 관련 문서를 먼저 스캔한다.
같은 결정을 두 번 하지 않기 위함.

호출 순서:
1. **`docs/business/biz-logic.md`** — 해당 Bounded Context 섹션 읽기
2. **`docs/architecture/adr/`** — 파일명에 주제 키워드 있으면 읽기
   - 예: 채팅 작업 → `ADR-005-chat-protocol.md`, `ADR-006-transaction-ticket-pattern.md`, `ADR-007-chat-transaction-payment-boundaries.md`
3. **`docs/research/`** — 해당 기능 관련 최신 리서치 문서 (파일명에 주제 키워드 포함) 읽기
   - 예: 채팅 작업 → `2026-04-15-chat-design.md`
4. **`docs/spec/api-spec.md`** — 해당 섹션 확인 (엔드포인트 이미 정의됐는지)
5. **`docs/diagram/schema.md`** — 관련 테이블 구조

읽은 내용 중 **이미 결정된 규칙·제약·계약**이 있으면 Intake 질문 대신 그대로 따른다.
문서가 충돌하면 사용자에게 확정 요청.

### 0.2 미확정 요구 질문

위 문서 조사 후에도 다음이 명확하지 않으면 **구현에 들어가지 말고 사용자에게 질문**한다.
추측으로 계획을 만들지 않는다.

- 어떤 Bounded Context에 속하는가? (`showcase`, `user`, `catalog`, `platform`, `common`, 향후 `chat`, `transaction-ticket`, `transaction`, `payment`)
- 새 API인가 기존 변경인가? (Breaking change 여부)
- 이벤트 발행·구독 관계가 바뀌는가?
- DB 스키마 변경이 있는가?
- 기존 상태기계(3D 생성, 주문, 거래, 결제 등)에 영향이 있는가?

불명확한 항목이 있으면 한 번에 묶어서 질문. 여러 차례 왕복 금지.

---

## 1. EXEC_PLAN 생성

**단일 진입점**: 사용자에게 task-name과 type을 받아서 스크립트를 실행한다.

```bash
bash scripts/start-task.sh <task-name> <type>
# type: feature | fix | refactor | chore | docs
```

이 스크립트가 원자적으로 다음을 생성한다:
- linked worktree (`../gearshow-<task>`)
- 브랜치 (`<type>/<task>`)
- 영구 플랜 파일 (`docs/agent/plans/YYYY-MM-DD-<task>.md`)
- worktree 루트의 EXEC_PLAN.md symlink
- 빈 포트 할당 (9000~9099)
- `.task/` 메타 디렉토리
- `logs/` 디렉토리
- 트레젝토리 기록 시작

스크립트 실행 후 EXEC_PLAN.md의 `<TODO:...>` 마커를 모두 채운다.
플랜이 미완성이면 `enforce-plan.sh` 훅이 코드 편집을 차단한다.

플랜 필수 섹션: 목표, 범위 In/Out, 변경 대상, 접근, 단계, 테스트 계획, 완료 기준, 롤백 전략, 위험도.

---

## 2. Worktree 구현

상세 정책: `references/worktree-policy.md`

- 모든 코드 편집은 linked worktree 내부에서 수행
- `main`/`master` 브랜치의 메인 작업 디렉토리에서 소스 수정 금지 (`enforce-worktree.sh`가 차단)
- 예외 허용: `docs/**`, `*.md`, `.claude/**`, `tools/**`, `.github/**`, `.githooks/**`
- 구현과 테스트는 **함께** 작성 — 테스트를 나중에 몰아서 쓰지 않음

**헥사고날 구현 순서**: `implement` 스킬(내부 도구)을 호출하여 domain → application → adapter 순서와 각 계층별 체크리스트를 참조한다.
`implement` 스킬은 `user_invocable: false`이므로 사용자가 직접 호출할 수 없으며, 이 Phase 2에서만 내부 참조된다.

코딩 규칙 전반:
- 패키지·SOLID·Domain Model·Anti-pattern : `references/coding-conventions.md`
- 예외 규칙 : `references/exception-rules.md`
- 테스트 규칙 : `references/test-rules.md`

---

## 3. 검증-a (계산적 센서)

**빠름, 결정론적, 병렬 가능**. 하나라도 실패하면 4단계 스킵하고 2단계 복귀.

```bash
cd backend
./gradlew compileJava   # 컴파일
./gradlew test          # 전체 테스트
./gradlew archTest      # ArchUnit 경계 검증
```

Stop 훅(`verify-and-block.sh`)이 작업 종료 시점에 `compileJava + archTest`를 자동 실행한다.
실패하면 `decision:"block"` + 에러 요약을 주입하여 자가수정 루프를 강제한다 (최대 3회).

---

## 4. 검증-b (추론적 센서)

**느림, LLM 기반, 순차**. 3단계 통과 후에만 실행.

| 서브에이전트 | 실행 조건 | 역할 |
|---|---|---|
| `code-reviewer` | 필수 | 네이밍·SRP·가독성·DTO/예외 규칙 |
| `architecture-reviewer` | 필수 | 헥사고날 경계·DDD·Aggregate 설계 |
| `database-optimizer` | Repository/JPQL/스키마 변경 시 | N+1·인덱스·트랜잭션 범위 |
| `test-writer` | Plan에 테스트 케이스 누락 시 | BDD 스타일 테스트 보강 |

**판정**:
- Critical 지적 → 2단계 복귀
- Nit 지적 → 별도 TODO 또는 후속 PR로 기록만 (진행 계속)

서브에이전트 호출 시 Bash로 `gh pr diff` 같은 컨텍스트를 함께 전달.

---

## 5. 커밋 + PR

위험도별 머지 정책: `references/risk-merge-policy.md`

요약:
| 변경 유형 | 자동 머지 | 사람 리뷰 |
|---|---|---|
| 오타/포맷/주석/테스트 추가 | ✅ | 선택 |
| 내부 리팩토링 | 🟡 서브에이전트 Critical 0일 때만 | 권장 |
| 새 API/유스케이스 | ❌ | 필수 |
| Breaking API 변경 | ❌ | 필수 |
| DB 스키마 변경 | ❌ | 필수 + 롤백 스크립트 |
| 상태기계 변경 | ❌ | 필수 + ADR |
| Kafka 이벤트 계약 변경 | ❌ | 필수 + 스펙 문서 갱신 |

커밋 메시지: 한글 "타입: 요약" 형식 (예: `feat: 쇼케이스 3D 생성 실패 알림 이벤트 추가`).
PR 생성은 `/pr-guide` 스킬에 위임.

---

## 6. Post-merge

- 트레젝토리 기록: `docs/agent/trajectories/YYYY-MM.log` 에 완료 라인 추가
- 각 단계 소요 시간·재시도 횟수·실패 원인·최종 커밋 해시
- 월 1회 `/review-gap-analysis` 스킬이 이 로그를 읽어 서브에이전트 정의를 보정

worktree 정리: `git worktree remove <path>`

---

## 에스컬레이션 (즉시 정지 조건)

상세: `references/escalation.md`

다음 중 하나라도 발생하면 즉시 작업을 멈추고 사용자에게 보고:

- 자가수정 3회 연속 실패
- EXEC_PLAN과 실제 구현의 diff 규모 150% 초과 (scope creep)
- `domain/`에 금지된 의존성 감지 (Spring/JPA import)
- DB 마이그레이션이 필요한 변경
- Breaking API 변경
- Kafka 이벤트 계약 변경
- 보안 경계 변경 (인증·인가·PII)
- 롤백 전략이 불명확한 변경

---

## Skill / Agent 맵

| Phase | 담당 | 비고 |
|---|---|---|
| 0 Intake | 이 오케스트레이터 | 질문·범위 확정 |
| 1 EXEC_PLAN | `scripts/start-task.sh` | 오케스트레이터가 실행 |
| 2 Worktree 구현 | `implement` 스킬 (내부) | Phase 2 전용, 직접 호출 금지 |
| 3 계산적 검증 | `Stop` 훅 자동 | `verify-and-block.sh` |
| 4 추론적 검증 | 4개 서브에이전트 병렬 | `code-reviewer`, `architecture-reviewer`, `database-optimizer`, `test-writer` |
| 5 커밋 + PR | `/pr-guide` 스킬 | |
| 5.5 PR 리뷰 (별도) | `/code-review` 스킬 | PR 번호 받아 GitHub 코멘트 등록 |
| 6 Post-merge / 학습 | `/review-gap-analysis` 스킬 | 월 1회 실행 |

---

## 금지 사항 (요약)

- Intake 단계 건너뛰고 바로 구현
- 테스트를 나중에 몰아서 작성
- 자가수정 루프에서 `--no-verify`, `-DskipTests`로 우회
- 실패 원인 분석 없이 brute-force 재시도
- 에스컬레이션 조건 충족인데 혼자 진행
- `main`/`master` 브랜치 메인 작업 디렉토리에서 소스 직접 수정
- 계획 없이 코드 편집 (훅이 차단)

상세 금지 사항:
- 코딩·아키텍처·에이전트 행동 : `references/coding-conventions.md` § 5
- 예외 처리 금지 사항 : `references/exception-rules.md`
- 테스트 금지 사항 : `references/test-rules.md`
