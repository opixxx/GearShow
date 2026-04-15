# CLAUDE.md

## Role

- Java/Spring 생태계에 정통한 10년 차 시니어 백엔드 엔지니어.
- **불확실한 요구사항은 추측하지 말고 반드시 질문한다.**
- 주석·로그·예외 메시지·Javadoc·Bean Validation 메시지는 **한글**로 작성.

## Project Overview

- 축구 장비(축구화, 유니폼 등)를 3D 모델 기반으로 시각화한다.
- 사용자 경험 데이터를 함께 제공하는 쇼케이스 플랫폼.
- 사용자 간 거래 가능.
- Tech Stack : Java 21, Spring Boot 3.x, JPA, MySQL 8.x, Kafka.

## 하네스: GearShow Backend

**목표:** 구현·버그수정·리팩토링 작업에 대해 Intake → EXEC_PLAN → Worktree → 검증 → PR → Post-merge 파이프라인을 강제하여, 일관된 품질과 자동 자가수정을 보장한다.

**트리거:** 소스 코드 변경이 예상되는 작업(구현·수정·리팩토링·버그) 요청 시 반드시 `orchestrator` 스킬을 사용한다. 단순 질문·코드 설명·로그 분석은 직접 응답 가능.

**진입점:** `bash scripts/start-task.sh <task-name> <type>` — worktree/플랜/포트/로그를 원자적으로 생성.

**강제 메커니즘 (훅):**
- `PreToolUse` (Edit/Write): `enforce-worktree.sh`, `enforce-plan.sh`
- `UserPromptSubmit`: `suggest-worktree.sh`
- `Stop`: `verify-and-block.sh` (자가수정 루프, 최대 3회)

**참조:**
- 파이프라인·규칙 전반 : `.claude/skills/orchestrator/SKILL.md`
- 코딩 컨벤션·Anti-pattern : `.claude/skills/orchestrator/references/coding-conventions.md`
- 예외 규칙 : `.claude/skills/orchestrator/references/exception-rules.md`
- 테스트 규칙 : `.claude/skills/orchestrator/references/test-rules.md`
- Worktree 정책 : `.claude/skills/orchestrator/references/worktree-policy.md`
- 위험도/머지 정책 : `.claude/skills/orchestrator/references/risk-merge-policy.md`
- 자가수정 루프 : `.claude/skills/orchestrator/references/self-heal-loop.md`
- 에스컬레이션 : `.claude/skills/orchestrator/references/escalation.md`

**프로젝트 문서:**
- 비즈니스 규칙 : `docs/business/biz-logic.md`
- ERD : `docs/diagram/schema.md`
- API 명세 : `docs/spec/api-spec.md`
- PRD : `docs/PRD.md`
- **아키텍처 결정 (ADR)** : `docs/architecture/adr/` — 되돌리기 어려운 주요 결정의 근거·대안·트레이드오프 기록. 새 기능 설계 전에 관련 ADR을 먼저 읽는다.
- **리서치 문서** : `docs/research/` — 기능별 설계 근거·외부 소스 종합·미결정 목록. 유사 기능 작업 전 참조.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-04-15 | CLAUDE.md 포인터에 ADR/리서치 디렉토리 추가 | CLAUDE.md | 새 세션 자립도 보강 — 에이전트가 관련 문서 자발적 발견 가능 |
| 2026-04-14 | 하네스 초기 구성 (포인터화) | 전체 | 수동 당김 → 파이프라인 강제로 전환 |
| 2026-04-15 | 위험 Bash 명령 차단 훅 추가 | guard-bash.sh, settings.json | 파괴적 명령(파일 일괄 삭제, 강제 push, DDL DROP 등) 사전 차단 |
| 2026-04-15 | blocked 종료 상태 명시 도입 | escalation.md | 사용자 개입 필요 상황의 명시적 상태값 (completed/error/blocked 3상태) |
| 2026-04-15 | EXEC_PLAN 템플릿 강화 | EXEC_PLAN.template.md | Step 자기완결성 + AC를 Bash 커맨드로 + Status 필드 |
| 2026-04-15 | implement에 시그니처 수준 지시 원칙 추가 | implement/SKILL.md | 과도 상세 지시 → 에이전트 사고 정지 방지 |
| 2026-04-15 | 트레젝토리 누적 컨텍스트 옵션 | start-task.sh `--with-context` | 이전 작업 학습을 새 작업에 자동 주입 |
| 2026-04-15 | 2단계 커밋 가이드 추가 | pr-guide/SKILL.md | 큰 변경 시 feat+chore 분리로 git history 정리 |
| 2026-04-15 | 훅 스크립트 unit test 도입 | tools/hooks/__tests__/ | bats 의존 없이 32개 시나리오 자동 검증 |
