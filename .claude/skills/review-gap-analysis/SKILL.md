---
name: review-gap-analysis
description: CodeRabbit PR 리뷰와 내부 리뷰 에이전트(architecture-reviewer, code-reviewer, database-optimizer) 결과를 비교하여 에이전트가 놓친 패턴을 발견하고, 원인 분석 + 에이전트 정의 보완까지 수행한다.
user_invocable: true
---

# Review Gap Analysis — 내부 리뷰 에이전트 보완

이 스킬은 **CodeRabbit 의 리뷰를 기준 진실(ground truth)** 로 삼아 내부 리뷰 에이전트의 사각지대를 찾아내고, 에이전트 정의(`.claude/agents/*.md`)를 자동 보완한다. 팀의 리뷰 품질을 시간에 따라 CodeRabbit 수준으로 수렴시키는 것이 목적이다.

## 실행 방법

```
/review-gap-analysis           → 현재 브랜치의 PR 번호 자동 감지
/review-gap-analysis 24        → PR #24 기준으로 분석
```

## 전제 조건

- 대상 PR 에 **CodeRabbit 리뷰가 이미 달려있어야** 한다 (보통 PR 생성 후 5~15분 내)
- 3개 에이전트가 같은 PR 을 최근에 리뷰한 기록이 있으면 재사용, 없으면 즉석 실행
- `gh` CLI + `jq` 설치 + GitHub 인증 완료

---

## 실행 절차

### Step 1: 대상 PR 및 CodeRabbit 리뷰 수집

```bash
# PR 번호 결정: 인자로 주어지지 않았으면 현재 브랜치의 PR 자동 조회
PR_NUMBER="${1:-$(gh pr view --json number --jq '.number')}"

# PR 기본 정보
gh pr view "$PR_NUMBER" --json title,headRefName,baseRefName,files,changedFiles

# CodeRabbit 리뷰 본문 + 인라인 코멘트 모두 수집
gh api "repos/{owner}/{repo}/pulls/$PR_NUMBER/reviews" \
  --jq '.[] | select(.user.login | test("coderabbit"; "i")) | {id, state, body, submitted_at}'

gh api "repos/{owner}/{repo}/pulls/$PR_NUMBER/comments" \
  --paginate \
  --jq '.[] | select(.user.login | test("coderabbit"; "i")) | {path, line, body}'

# "Walkthrough" / "Summary of changes" 는 제외하고, 실제 지적사항만 추출
```

**CodeRabbit 리뷰가 없는 경우**:
- PR 이 방금 생성되었을 가능성 → 5~10분 대기 후 재시도 안내
- CodeRabbit 이 비활성화된 저장소일 수 있음 → `.coderabbit.yaml` 존재 여부 확인

### Step 2: CodeRabbit 지적사항 구조화

각 코멘트를 다음 JSON 스키마로 정규화:

```json
{
  "source": "coderabbit",
  "file": "backend/src/main/java/.../Showcase3dModel.java",
  "line": 107,
  "severity": "nitpick" | "refactor" | "potential_issue" | "🛠️" | "⚠️",
  "category": "architecture" | "code" | "database" | "security" | "test" | "naming" | "other",
  "summary": "1줄 요약",
  "detail": "전체 코멘트",
  "suggested_fix": "있으면 코드 조각"
}
```

**카테고리 자동 분류 힌트** (키워드 기반, 애매하면 `other`):
- **architecture**: `의존 방향`, `헥사고날`, `바운디드 컨텍스트`, `port`, `adapter`, `DDD`, `aggregate`, `import 경로`
- **code**: `SOLID`, `SRP`, `Clean Code`, `네이밍`, `예외`, `exception`, `20줄`, `null 체크`, `IllegalArgument`
- **database**: `인덱스`, `N+1`, `transaction`, `@Transactional`, `@Lob`, `JPA`, `쿼리`, `select`, `merge`
- **security**: `민감정보`, `SQL injection`, `인증`, `토큰`, `secrets`
- **test**: `테스트`, `assertion`, `mock`, `stub`, `BDD`, `커버리지`
- **naming**: `이름`, `네이밍`, `rename`, `camelCase`

### Step 3: 내부 에이전트 최근 리뷰 수집 (또는 즉석 실행)

```bash
# 최근 대화 맥락에 3개 에이전트 결과가 있으면 사용
# 없으면 즉석 실행 (architecture-reviewer, code-reviewer, database-optimizer 병렬)
```

즉석 실행 시 Task 툴로 3개 에이전트를 background 병렬 실행:
- 각 에이전트에게 동일 PR 범위 (git diff main...HEAD) 를 명시
- 출력 형식을 CodeRabbit 스키마와 동일하게 요청 (file/line/category/severity/summary)

### Step 4: 비교 매칭

**매칭 기준**:
1. **동일 파일 + 라인 ±5 범위** 로 후보 수집
2. 후보가 있으면 **summary 유사도** (키워드 겹침) 로 매칭
3. 매칭되지 않은 CodeRabbit 지적 = **내부 에이전트 누락**

**출력 예시 테이블**:

| # | CR 심각도 | 파일:라인 | 카테고리 | CR 요약 | 매칭된 에이전트 | 상태 |
|---|---|---|---|---|---|---|
| 1 | 🛠️ refactor | Showcase3dModel.java:107 | code | IllegalArgumentException 직접 사용 | code-reviewer | ✅ 매칭 |
| 2 | ⚠️ potential | OutboxMessagePersistenceAdapter.java:40 | architecture | JPA 엔티티 직접 조작 (도메인 우회) | architecture-reviewer | ✅ 매칭 |
| 3 | 🛠️ refactor | OutboxMessageJpaEntity.java:62 | database | `@Lob` 대신 `columnDefinition` | database-optimizer | ✅ 매칭 |
| 4 | nitpick | RequestModelGenerationFacade.java:45 | code | Stream → Collectors.toList() 대신 .toList() | **없음** | ❌ **누락** |
| 5 | ⚠️ potential | KafkaProducerConfig.java:55 | database | acks=all 단일 브로커 주석 불일치 | **없음** | ❌ **누락** |

### Step 5: 누락 원인 분석

각 누락 항목에 대해 다음 질문을 통해 원인 카테고리를 식별:

| 원인 카테고리 | 질문 | 처방 |
|---|---|---|
| **범위 미커버** | 에이전트가 이 파일/디렉토리를 리뷰 범위로 인식했나? | `.md` 파일에 명시적 파일 글로브 추가 |
| **체크리스트 누락** | 이 유형의 체크가 에이전트 검증 포인트에 있나? | 새 체크포인트 항목 추가 |
| **표현 차이** | 에이전트가 같은 문제를 다른 용어로 언급했나? | 매칭 키워드 사전 보완 (false negative 해소) |
| **맥락 부족** | CodeRabbit 은 알지만 에이전트에겐 없는 프로젝트 컨텍스트 때문인가? | CLAUDE.md 참조를 에이전트 프롬프트에 추가 |
| **의도된 무시** | 에이전트 규칙상 의도적으로 무시한 Minor 인가? | 무시, 보고만 |

### Step 6: 에이전트 정의 보완 (`.claude/agents/*.md` 수정)

**작업 방식**:
1. 각 에이전트 파일을 읽는다
2. **"## 검증 포인트"** 또는 **"## 검사 항목"** 섹션을 찾는다
3. 누락된 체크포인트를 "## 추가 학습 사항 (YYYY-MM-DD review-gap-analysis)" 섹션에 append
4. 기존 체크포인트와 중복되면 skip
5. 각 항목에 CR 예시 file:line 을 근거로 남긴다

**예시 추가 블록**:
```markdown
## 추가 학습 사항 (2026-04-10 review-gap-analysis PR#24)

### code-reviewer 에 추가
- **Stream 종료 연산 현대화**: `.collect(Collectors.toList())` 대신 Java 16+ `.toList()` 권장 (불변 리스트, 간결성)
  - 근거: PR#24 CodeRabbit nitpick (RequestModelGenerationFacade.java:45)
  - 체크: `Grep "Collectors\.toList\(\)"` 0건이어야 함

### database-optimizer 에 추가
- **브로커 설정 주석 정합성**: `acks=all` + `min.insync.replicas=1` 조합은 단일 브로커 환경에서 의미 없음. 주석에서 "운영 전환 시 >=2 필수" 를 명시했는지 확인
  - 근거: PR#24 CodeRabbit potential_issue (KafkaProducerConfig.java:55)
```

⚠️ **수정 전 사용자 승인**: 에이전트 `.md` 파일 수정은 파괴적이므로, 보완 내용을 먼저 **제시하고 사용자 승인** 후 Edit 한다.

### Step 7: 결과 보고

마지막에 다음 형식으로 정리:

```
## 📊 Review Gap Analysis — PR #24

### 통계
- CodeRabbit 지적 총: 23건
- 내부 에이전트가 이미 잡은 것: 18건 (78%)
- **누락**: 5건 (22%)
  - architecture: 0건
  - code: 3건 (nitpick 2, potential 1)
  - database: 2건 (nitpick 1, refactor 1)

### 누락 원인 분포
- 체크리스트 누락: 3건
- 범위 미커버: 1건
- 맥락 부족: 1건

### 보완 제안 파일
- .claude/agents/code-reviewer.md — 3개 항목 추가
- .claude/agents/database-optimizer.md — 2개 항목 추가

사용자가 보완 적용 승인하시면 파일 수정 진행합니다.
```

---

## 주의사항 / Edge Cases

### CodeRabbit 코멘트 노이즈 처리
- `🔇 Additional comments not posted` / `⚡ Walkthrough` / `Summary of changes` / `📝 Generated check` 같은 메타 코멘트는 제외
- `_:thumbsup:_` / 코드 제안만 있고 설명 없는 경우는 분류 불가 → `other` 로 분류 후 수동 검토

### 중복 매칭 방지
- CodeRabbit 이 같은 이슈를 본문 요약 + 인라인 코멘트 양쪽에 남기는 경우가 흔함 → file:line 으로 dedup
- 에이전트가 같은 파일의 여러 이슈를 하나로 묶어 보고한 경우 → 라인 범위 ±10 까지 완화 적용

### False Positive 처리 (에이전트가 잘못 지적한 경우)
- 이 스킬의 목적은 "에이전트가 놓친 것" 을 찾는 것이지 에이전트 지적의 정확성 검증이 아님
- 에이전트 지적 중 CodeRabbit 이 언급하지 않은 것은 **"에이전트가 더 엄격한 기준"** 일 가능성이 크므로 그대로 둔다

### 에이전트 정의 파일 위치
- 기본: `.claude/agents/{agent-name}.md`
- 에이전트가 글로벌에만 있고 로컬에 없으면 먼저 로컬로 복사 후 수정 (글로벌 프롬프트 오버라이드)

---

## 결과물 예시 구조

실행 후 다음 세 가지가 산출된다:

1. **비교 보고서** (conversation 에 출력)
2. **제안된 에이전트 보완안** (사용자 승인 대기)
3. **승인 시 수정된 `.claude/agents/*.md` 파일**

---

## 확장 가능성 (나중에 추가)

- **Historical gap tracking**: 여러 PR 결과를 누적해 "자주 놓치는 패턴 TOP 10" 도출
- **자동 에이전트 회귀 테스트**: 보완 후 같은 PR 재실행 시 놓쳤던 이슈를 이제 잡는지 검증
- **CodeRabbit 이외 소스 추가**: 수동 시니어 리뷰, Sonar, CheckStyle 결과 등도 비교 대상에 포함
