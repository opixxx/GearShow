---
name: code-review
description: >
  GitHub PR에 대해 4개 서브에이전트(code-reviewer, architecture-reviewer, database-optimizer, test-writer)를
  병렬 호출하여 통합 리뷰를 생성하고 PR 코멘트로 등록한다. (1) "PR 리뷰", "코드리뷰", "/code-review"
  요청, (2) PR 번호가 언급된 리뷰 요청, (3) 머지 전 품질 점검 시 사용. 서브에이전트 누적 학습(추가 학습 섹션)이
  자동으로 리뷰에 반영된다.
user_invocable: true
---

# PR 코드리뷰 — 서브에이전트 오케스트레이션

이 스킬은 **직접 리뷰하지 않는다**. 4개 서브에이전트를 병렬 호출하여 각자의 전문 영역을 검토하게 하고,
결과를 통합하여 GitHub PR에 코멘트로 등록한다.

각 서브에이전트가 `## 추가 학습 (review-gap-analysis)` 섹션에 누적한 패턴이 자동으로 반영되므로,
리뷰 품질이 시간에 따라 개선된다.

## 실행 방법

```
/code-review          → 현재 브랜치의 PR을 리뷰
/code-review 27       → PR #27을 리뷰
```

---

## Step 1: PR 컨텍스트 수집

```bash
# PR 번호 결정
PR_NUMBER="${1:-$(gh pr view --json number --jq '.number')}"

# PR 기본 정보
gh pr view "$PR_NUMBER" --json title,body,headRefName,baseRefName,files,additions,deletions,state

# 변경 diff
gh pr diff "$PR_NUMBER"

# CI 상태
gh pr checks "$PR_NUMBER"
```

확인할 것:
- PR 설명과 변경 목적
- 변경 규모 (400줄 초과 시 분할 권고를 리뷰에 포함)
- CI 통과 여부 (빌드 실패 상태면 리뷰 전에 안내)
- **EXEC_PLAN 존재 여부** — PR 본문에 `docs/agent/plans/` 링크가 있으면 함께 참조

---

## Step 2: 4개 서브에이전트 병렬 호출

Task 툴로 네 개를 동시에 호출한다 (`run_in_background: true` 지양 — 결과를 즉시 통합해야 함).

| 서브에이전트 | 담당 영역 | 호출 조건 |
|---|---|---|
| `code-reviewer` | SOLID · 네이밍 · 보안 · DTO/예외 · 클린 코드 · 엣지 케이스 | 항상 |
| `architecture-reviewer` | 헥사고날 경계 · DDD · BC 격리 · Aggregate 설계 · 계층 일관성 | 항상 |
| `database-optimizer` | N+1 · 인덱스 · 쿼리 최적화 · 트랜잭션 범위 | Repository/JPQL/스키마 변경 시 |
| `test-writer` (**점검만**, 작성 금지) | 테스트 커버리지 · Happy/Unhappy · BDD 스타일 · 격리 | 항상 |

### 각 에이전트 호출 프롬프트 형식

```
PR #{번호} 를 리뷰하세요.

- 변경 파일: {gh pr view로 얻은 목록}
- 변경 diff: gh pr diff {PR번호} 로 직접 수집하세요
- 대상 브랜치: {headRef} → {baseRef}

리뷰 기준:
- .claude/agents/{agent-name}.md 의 체크리스트 + 추가 학습 섹션 모두 적용
- 출력 형식: file:line, 심각도(Critical/Major/Minor), 카테고리, 위반 내용, 수정 제안

`test-writer` 에게만 추가: 이 호출은 점검(audit) 목적이며 테스트 파일을 Edit/Write 하지 마세요.
                       테스트 적정성 평가만 수행합니다.
```

### `database-optimizer` 조건부 실행

다음 중 하나라도 해당하면 호출:
- `backend/src/main/java/**/adapter/out/persistence/**` 변경
- `backend/src/main/java/**/domain/repository/**` 변경
- `.sql`, Flyway/Liquibase 마이그레이션 파일 변경
- JPA 엔티티(`*JpaEntity.java`) 변경
- JPQL/Native Query 포함 메서드 변경

해당 없으면 리뷰 결과에 "database: N/A (변경 없음)" 로 표기.

---

## Step 3: 결과 통합

### 3-1. Dedup

같은 file:line에 여러 에이전트가 동일 이슈를 지적한 경우 (범위 ±3라인) 하나로 병합. 가장 높은 심각도와 가장 상세한 설명을 채택.

### 3-2. 심각도 레이블 변환

에이전트 용어 → GitHub 리뷰 레이블 매핑:

| 에이전트 용어 | GitHub 레이블 | 머지 영향 |
|---|---|---|
| CRITICAL | 🔴 `[blocking]` | 반드시 수정 |
| MAJOR | 🟡 `[important]` | 수정 권장 |
| MINOR | 🟢 `[nit]` | 선택 |
| (대안 제시) | 💡 `[suggestion]` | 선택 |
| (칭찬) | 🎉 `[praise]` | — |

### 3-3. 카테고리 집계

에이전트별 요약 생성 (위반 건수만):

```
## 📊 리뷰 결과 요약

| 에이전트 | Critical | Major | Minor | 상태 |
|---|---|---|---|---|
| code-reviewer | 0 | 2 | 3 | 수정 권장 |
| architecture-reviewer | 1 | 0 | 0 | 🔴 머지 차단 |
| database-optimizer | 0 | 1 | 0 | 수정 권장 |
| test-writer | 0 | 1 | 2 | 수정 권장 |
| **합계** | **1** | **4** | **5** | **🔄 Request Changes** |
```

---

## Step 4: GitHub PR 코멘트 등록

### 4-1. 리뷰 본문 형식

```markdown
## 🤖 자동 코드리뷰 결과

{Step 3-3 요약 표}

### 🔴 Blocking ({개수}건)
{CRITICAL 항목 — file:line · 카테고리 · 위반 내용 · 수정 제안 · 에이전트명}

### 🟡 Important ({개수}건)
{MAJOR 항목}

### 🟢 Nit ({개수}건)
{MINOR 항목}

### 💡 Suggestions
{대안 제시}

### 🎉 Good Parts
{칭찬할 부분}

---

**리뷰 에이전트**: code-reviewer · architecture-reviewer · database-optimizer · test-writer
**누적 학습 반영**: 각 에이전트의 `## 추가 학습 (review-gap-analysis)` 섹션 포함
```

### 4-2. 판정 결정

| 상태 | 조건 | 명령 |
|---|---|---|
| ✅ Approve | Critical 0건 + Major 0건 | `gh pr review {N} --approve --body "..."` |
| 💬 Comment | Critical 0건 + Major 있음 | `gh pr review {N} --comment --body "..."` |
| 🔄 Request Changes | Critical 1건 이상 | `gh pr review {N} --request-changes --body "..."` |

### 4-3. 인라인 코멘트

특정 라인에 대한 구체 피드백은 본문 리뷰가 아니라 **인라인 코멘트**로 등록:

```bash
gh api repos/{owner}/{repo}/pulls/{PR}/comments \
  -f body="🔴 [blocking] ..." \
  -f commit_id="$(gh pr view {PR} --json headRefOid -q .headRefOid)" \
  -f path="{file}" \
  -F line={line}
```

인라인 코멘트 기준:
- Critical/Major: 반드시 인라인
- Minor/Suggestion: 본문 요약만 (노이즈 방지)

---

## 이 스킬이 하지 않는 것

- **직접 체크리스트 대조** — 서브에이전트 고유 영역이므로 각 `.md` 파일에서 관리
- **코드 수정** — 리뷰만 수행, 수정은 PR 작성자 또는 `orchestrator`가 담당
- **테스트 추가/수정** — `test-writer`는 점검만 수행 (작성 스킬은 오케스트레이터 Phase 4에서만)
- **빌드/CI 실행** — CI 상태는 `gh pr checks`로 조회만

---

## 에러 대응

| 상황 | 대응 |
|---|---|
| PR 번호 없음 | `gh pr view`로 현재 브랜치 PR 자동 조회, 없으면 사용자에게 안내 |
| CI 실패 상태 | 리뷰 전에 CI 실패 원인을 리뷰 본문 상단에 명시 |
| 변경 파일 0개 | "리뷰할 변경 없음" 안내 후 종료 |
| 에이전트 1개 타임아웃 | 해당 에이전트는 "N/A (타임아웃)"으로 표기하고 나머지 3개로 진행 |
| PR이 이미 머지됨 | "이미 머지된 PR, post-hoc 리뷰로 기록만 진행" 안내 후 코멘트만 등록 |

---

## 참조

- 각 에이전트 정의: `.claude/agents/{code-reviewer,architecture-reviewer,database-optimizer,test-writer}.md`
- 리뷰 갭 분석: `/review-gap-analysis` 스킬 (월 1회)
- 오케스트레이터 Phase 4와의 관계: `.claude/skills/orchestrator/SKILL.md`
  (오케스트레이터 Phase 4는 **구현 중 리뷰**, 이 스킬은 **PR 생성 후 리뷰**. 호출 시점이 다름)
