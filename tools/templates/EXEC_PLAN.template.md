# EXEC_PLAN: {{TASK}}

- **Type**: {{TYPE}}
- **Risk**: <TODO: Safe | Caution | Risky | High>
- **Created**: {{DATE}}
- **Branch**: {{BRANCH}}
- **Worktree**: {{WT}}
- **Port**: {{PORT}}

---

## 1. 목표 (Goal)

<TODO: 무엇을·왜 — 1~3 문장으로 작성. "어떻게"는 아래 섹션에>

## 2. 범위 (Scope)

### In
- <TODO: 이번 PR에서 반드시 포함하는 것>

### Out
- <TODO: 명시적으로 제외하는 것 — scope creep 방지>

## 3. 변경 대상 (Affected)

- **domain/**: <TODO: 변경·추가 파일. 없으면 "없음">
- **application/**: <TODO>
- **adapter/**: <TODO>
- **docs/**: <TODO: 함께 갱신할 문서. 없으면 "없음">

## 4. 접근 (Approach)

<TODO: 기술적 선택과 근거. 대안이 있었다면 왜 이 선택인지 1~2 문단>

## 5. 단계 (Steps)

- [ ] 1. <TODO: 첫 커밋 단위>
- [ ] 2. <TODO: 두 번째 커밋 단위>
- [ ] 3. <TODO: 테스트 작성 — Happy + Unhappy>

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: <TODO: 성공 케이스 1개 이상>
- **Unhappy Path**: <TODO: 실패/예외 케이스 1개 이상>
- **추가 검증**: <TODO: 필요 시 ArchUnit·성능·보안. 없으면 "없음">

## 7. 완료 기준 (Done Criteria)

- [ ] 모든 Step 체크
- [ ] `./gradlew build` 통과
- [ ] `./gradlew archTest` 통과
- [ ] code-reviewer / architecture-reviewer Critical 지적 0건
- [ ] 관련 문서 갱신 완료

## 8. 롤백 전략 (Rollback)

<TODO: 스키마 변경·이벤트 계약 변경·공개 API 변경이 있을 때만 작성. 없으면 "해당 없음">

---

> 이 계획이 완성되기 전까지는 코드 편집이 차단됩니다 (enforce-plan.sh).
> `<TODO:...>` 마커를 모두 제거한 뒤 구현을 시작하세요.
