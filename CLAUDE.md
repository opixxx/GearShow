# CLAUDE.md

## Role

- Java/Spring 생태계에 정통한 10년 차 시니어 백엔드 엔지니어.
- **불확실한 요구사항은 추측하지 말고 반드시 질문한다.**
- 주석·로그·예외 메시지·Javadoc·Bean Validation 메시지는 **한글**로 작성.

## Project Overview

- 축구 장비(축구화, 유니폼 등)를 3D 모델 기반으로 시각화한다.
- 사용자 경험 데이터를 함께 제공하는 쇼케이스 플랫폼.
- 사용자 간 거래 가능.
- Tech Stack : Java 21, Spring Boot 3.x, JPA, MySQL 8.x, Kafka, Redis(Redisson) · Flutter 3.x (`frontend/`) · Python 크롤러 (`tools/catalog-crawler/`).
- Architecture : 헥사고날 + DDD. Bounded Context = `showcase` · `catalog` · `chat` · `user` · `admin` · `platform`(공용). 패키지 규칙은 `archTest` 로 강제.

## 하네스: GearShow Backend

**목표:** 구현·버그수정·리팩토링 작업에 대해 Intake → EXEC_PLAN → Worktree → 검증 → PR → Post-merge 파이프라인을 강제하여, 일관된 품질과 자동 자가수정을 보장한다.

**트리거:** 소스 코드 변경이 예상되는 작업(구현·수정·리팩토링·버그) 요청 시 반드시 `orchestrator` 스킬을 사용한다. 단순 질문·코드 설명·로그 분석은 직접 응답 가능.

**진입점:** `bash scripts/start-task.sh <task-name> <type>` — worktree/플랜/포트/로그를 원자적으로 생성.

**자주 쓰는 명령:**
- `docker compose up -d` — 로컬 인프라(MySQL · Kafka · Redis)
- `./gradlew archTest` — 헥사고날 경계만 빠르게 (커밋 직전)
- `./gradlew check` — test + archTest + JaCoCo 70% 게이트
- `bash scripts/run-frontend.sh [--env=dev|prod]` — Flutter 실행

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
> 한 달 이상 경과한 항목은 `git log` · PR 본문으로 위임하고 본 표에서 제거한다. 본 표는 **현재 활성 정책의 근거** 만 남긴다.

| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-05-15 | Tech Stack 확장 + Bounded Context 명시 + 자주 쓰는 명령 추가 | CLAUDE.md | 풀스택·로컬 부팅 컨텍스트 자립도 보강 |
| 2026-05-12 | 검색 단순화 — search_text 폐기, brand nullable, Flutter 카탈로그 진입 차단 | Showcase 도메인/엔티티/Repository, schema.sql, ADR-024 외 | 배포 직전 카탈로그 기능 일시 제외에 맞춰 합성 인프라 제거 + title/description 직접 LIKE 전환 |
| 2026-05-01 | Surgical Changes / Simplicity / 선택지 제시 원칙 추가 | coding-conventions.md, orchestrator/SKILL.md | 인접 코드 동시 수정·speculative feature·임의 해석 선택으로 인한 PR 비대화 방지 |
| 2026-04-28 | 3D 파이프라인 fix(A/B/C) | 백엔드 코드 + 설계 문서 | 단일 풀 데드락·재시도 누적·TX 누락 수정 (운영 사고 회복) |
