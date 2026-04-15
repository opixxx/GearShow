# EXEC_PLAN: {{TASK}}

- **Type**: {{TYPE}}
- **Status**: pending  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: <TODO: Safe | Caution | Risky | High>
- **Created**: {{DATE}}
- **Branch**: {{BRANCH}}
- **Worktree**: {{WT}}
- **Port**: {{PORT}}

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

<TODO: 무엇을·왜 — 1~3 문장으로 작성. "어떻게"는 아래 섹션에>

## 2. 범위 (Scope)

### In
- <TODO: 이번 PR에서 반드시 포함하는 것>

### Out
- <TODO: 명시적으로 제외하는 것 — scope creep 방지. "후속 PR로", "별도 작업으로" 등>

## 3. 변경 대상 (Affected)

- **domain/**: <TODO: 변경·추가 파일. 없으면 "없음">
- **application/**: <TODO>
- **adapter/**: <TODO>
- **docs/**: <TODO: 함께 갱신할 문서. 없으면 "없음">

## 4. 접근 (Approach)

<TODO: 기술적 선택과 근거. 시그니처 수준만 — 함수/클래스 인터페이스, 핵심 규칙, 의사결정.
구현체 디테일은 에이전트 재량에 맡긴다. 단 멱등성·보안·데이터 무결성 같은 양보 불가 규칙은 명시.>

## 5. 단계 (Steps)

> 각 step은 **자기완결적**이어야 한다. "이전 대화에서 논의한 바와 같이" 같은 외부 컨텍스트 참조 금지.
> 필요한 정보는 전부 step 안에 적는다 — 다른 세션에서 이 step만 읽어도 작업 가능해야 함.
> 간단한 작업은 한 step으로, 큰 작업(여러 계층/여러 모듈)은 여러 step으로 분할.

### Step 1: <kebab-case-slug>

**읽어야 할 파일** (작업 전 파악):
- <TODO: 관련 spec 문서 경로>
- <TODO: 기존 코드 경로 — 재사용 대상/영향 받는 파일>
- <TODO: 이전 step에서 생성된 파일 (있으면)>

**작업**:
<TODO: 시그니처 수준 지시.
- 클래스/메서드 인터페이스
- 핵심 규칙 (양보 불가 항목)
- 외부 의존 (Port 인터페이스, ErrorCode 등)
구현체 디테일은 에이전트 판단.>

**AC (Bash로 표현)**:
```bash
cd backend
./gradlew compileJava   # 컴파일 통과
# 추가 검증이 있으면 명시 (예: ./gradlew test --tests "*XxxServiceTest*")
```

**금지사항**:
- <TODO: "X를 하지 마라. 이유: Y" 형식>
- 기존 테스트를 깨뜨리지 마라

### Step 2: <kebab-case-slug>

**읽어야 할 파일**:
- <TODO>
- Step 1 산출물: <TODO 경로>

**작업**:
<TODO>

**AC**:
```bash
<TODO>
```

**금지사항**:
- <TODO>

<!-- 필요한 만큼 Step 추가 -->

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: <TODO: 성공 케이스 — Cucumber 시나리오 또는 통합 테스트>
- **Unhappy Path**: <TODO: 실패/예외 — 최소 1개>
- **추가 검증**: <TODO: ArchUnit·성능·보안. 없으면 "없음">

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

모든 step 완료 후 다음이 모두 통과해야 `Status: completed` 로 마무리:

```bash
cd backend
./gradlew build           # 컴파일 + 전체 테스트 + 커버리지(70%) + ArchUnit
```

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건
- [ ] architecture-reviewer Critical 지적 0건
- [ ] database-optimizer Critical 지적 0건 (Repository 변경 있을 때)
- [ ] 관련 문서 갱신 완료
- [ ] EXEC_PLAN의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

<TODO: 스키마 변경·이벤트 계약 변경·공개 API 변경이 있을 때만. 없으면 "해당 없음".
- 어떤 명령으로 되돌리는가
- 실행 순서
- 데이터 손실 여부>

---

## ⚠️ 작성 규칙 요약

1. **자기완결성**: 외부 대화 참조 금지. 필요한 모든 정보를 명시.
2. **시그니처 수준 지시**: "어떻게 구현하라" 보단 "무엇을, 어떤 인터페이스로". 구현체는 에이전트 재량.
3. **AC는 Bash 커맨드**: "동작해야 한다" 같은 추상 서술 금지. 실행 가능한 명령으로.
4. **금지사항은 구체적으로**: "조심해라" 대신 "X를 하지 마라. 이유: Y" 형식.
5. **Step 분할**: Scope 최소화 — 한 step에 한 레이어/모듈. 여러 모듈 동시면 step 쪼개기.

> 이 EXEC_PLAN의 모든 `<TODO:...>` 마커가 채워질 때까지 코드 편집이 차단됩니다 (`enforce-plan.sh`).
