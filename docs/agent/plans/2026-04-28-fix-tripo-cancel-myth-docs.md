# EXEC_PLAN: fix-tripo-cancel-myth-docs

- **Type**: docs
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Safe
- **Created**: 2026-04-28 16:00
- **Branch**: docs/fix-tripo-cancel-myth-docs
- **Worktree**: /Users/opix/GearShow/../gearshow-fix-tripo-cancel-myth-docs
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

ADR-011 ④ "Tripo 사후 cancel" 전략과 그에 의존하는 설계 문서·코드 주석·메모리의 진술이 **잘못된 가정 (Tripo cancel API 가 동작한다)** 위에 서 있었음을 정정한다. cancel 이 사용 불가하므로 4계층 멱등성은 사실상 **3계층 + Tripo POST/TX2 사이 실패 시 1회분 크레딧 손실 수용** 으로 운영 의미가 바뀐다. 코드 동작은 변경 없음 (현재 코드는 이미 cancel 을 호출하지 않는다 — 문서·주석만 사실에서 어긋나 있음).

## 2. 범위 (Scope)

### In
- `docs/architecture/adr/ADR-011-3d-pipeline-multilayer-idempotency.md` 의 §2.④, §2 양보 불가 규칙, §3 Alt C, §4 결과(긍정/부정/검증), §5 잔존 리스크 정정 + 하단 `## 6. 변경 이력` 신설
- `docs/research/2026-04-23-3d-generation-pipeline-design.md` 의 §4 표/④ 상세, §8.4 의사코드 중복 cancel 블록, §11 P1-G-γ 행, §12 #1/#2, §14 변경 이력에 v1.2 추가
- `backend/src/main/java/com/gearshow/backend/showcase/application/service/PrepareWorkflowService.java` 의 `preservePendingTask` Javadoc (현재 라인 211~214) 정정. **메서드 본문 변경 없음**
- 메모리 정정 (worktree 외, ~/.claude/projects/...):
  - `project_3d_pipeline_phase1.md` 라인 36 (P1-G-γ 항목), 라인 55 (§12 #1/#2 진술)
  - `project_3d_pipeline_deferred_refactors.md` 라인 129 (P1-H 백로그 (c) Reconcile-γ Tripo cancel)

### Out
- 코드 동작 변경 일체 (preservePendingTask 의 retry 추가, ALERT 메트릭 신설 등은 별도 후속 PR)
- ADR supersede / 새 ADR 작성 (in-place 정정으로 합의됨, Q1=A)
- `tripo_pending_task` 테이블 구조 변경 또는 제거 (M3 구조 단순화는 별도 ADR 후 보류)
- `cancelTask` 포트 메서드 신설 (Tripo cancel 자체가 불가능하므로 영구 미진행)
- 설계 §8.4 의 "중복 task 정리 별도 배치" 의사코드 블록 — 단순 삭제할지 "❌ 미구현 (cancel 불가)" 마커로 둘지는 단계 1에서 판단 (정정 일관성 위해 삭제 권장)

## 3. 변경 대상 (Affected)

- **domain/**: 없음
- **application/**: `PrepareWorkflowService.java` (Javadoc 1곳, 코드 본문 무변경)
- **adapter/**: 없음
- **docs/**: `ADR-011-3d-pipeline-multilayer-idempotency.md`, `2026-04-23-3d-generation-pipeline-design.md`
- **memory** (worktree 외): `project_3d_pipeline_phase1.md`, `project_3d_pipeline_deferred_refactors.md`

## 4. 접근 (Approach)

**핵심 사실 (모든 정정의 근거)**: Tripo `POST /v2/task/{taskId}/cancel` 엔드포인트는 공식 API 문서에 존재하지 않는다 (2026-04 기준 재확인). 사용자 확인 — Q2=a.

**정정 원칙**:
- 사실관계 정정만 — 새로운 결정/대안 추가 금지
- ADR-011 의 ①②③ 결정은 그대로 유효, ④ 만 "cancel 의존" → "손실 수용 + 운영 알림" 으로 갱신
- 각 문서의 변경 이력 섹션에 사유와 함께 신버전 마킹 (ADR-011 v1.0→v1.1, design doc v1.1→v1.2)
- 메모리는 P1-G-γ 항목을 "보류" 가 아니라 "영구 폐기 (cancel 미지원)" 로 마킹 (Q3=a)

**양보 불가 규칙** (정정 후에도 불변):
- ①②③ 의 "양보 불가 규칙" 5개 줄은 그대로 유지 — `Idempotency-Key` persist, 결정적 event_id, processed_message 완료 시점 INSERT, content_hash 10분 창
- ④ 의 양보 불가 규칙 ("Tripo 에 Idempotency-Key 헤더 붙이지 않음") 도 그대로 유효

**잔존 리스크 재기술**: ADR-011 §4 부정 의 "Tripo cancel 호출이 success 직전에 들어가면 race" → "Tripo POST 성공 후 `tripo_pending_task` INSERT 또는 TX2 가 실패하면 stranded task 1개 발생, cancel 불가로 1회분 크레딧 영구 손실. 운영 측 일일 balance 모니터링 + `failure_code=TX2_DB_FAILED` 카운터로 인지" 로 대체.

## 5. 단계 (Steps)

### Step 1: adr-011-correct

**읽어야 할 파일**:
- `docs/architecture/adr/ADR-011-3d-pipeline-multilayer-idempotency.md` 전체

**작업**:
ADR-011 의 다음 7개 위치를 정정한다 (모든 사유: Tripo cancel 미지원).

1. **§2.④ "Tripo — 사후 cancel"** (라인 69~75): 제목을 `④ Tripo — 손실 수용 + 운영 모니터링` 으로 변경. 본문은 다음 구조:
   - 첫 줄: "Tripo 는 `Idempotency-Key` 헤더와 `cancel` 엔드포인트 모두 미지원 (2026-04 재확인). 따라서 ④ 계층은 '중복/오발행 task 의 사후 정리' 가 아니라 '발생 빈도 최소화 + 잔존 손실 수용' 으로 정의된다."
   - 항목 1 (선저장): 그대로 유지
   - 항목 2 (Reconcile 의 PREPARING 복구): 그대로 유지
   - 항목 3 (이전: "중복 task 를 cancel"): 다음으로 대체 — "선저장 자체가 실패하거나 (`tripo_pending_task` INSERT DataAccessException) Tripo POST 직후 워커가 죽으면 task_id 가 유실된 stranded task 가 1개 발생한다. **cancel 불가** 로 인해 Tripo 백그라운드에서 task 가 완료되며 1회분 크레딧이 영구 손실된다. 운영 측 일일 balance 모니터링 + `failure_code=TX2_DB_FAILED` 카운터로 사후 인지한다."
   - 마지막 줄 ("과금 확정 시점이 success 라는 사실이 이 전략을 성립시킴"): 삭제 (전제가 무너짐).

2. **§2 "양보 불가 규칙"** (라인 77~83): 4번째 줄 (`④ Tripo 에 Idempotency-Key 헤더 붙이지 않음. ...`) 만 유지하고 그 외는 그대로. 추가 한 줄: "**`tripo_pending_task` INSERT 실패 시 Tripo cancel 시도 금지** — cancel API 미지원이라 호출해봐야 의미 없고 오해 소지만 발생."

3. **§3.C "Tripo `Idempotency-Key` 헤더 의존"** (라인 99~103): "장점" 줄을 삭제 (전제가 무너졌으므로 "원천 차단" 시나리오 자체가 의미 없음). 단점에 한 줄 추가: "`cancel` 엔드포인트도 미지원 → 사후 정리 경로도 없음."

4. **§4 긍정** (라인 119~125): 첫 항목 "이중 과금 리스크 운영 수용 수준 — ①②③으로 중복 호출 자체를 줄이고, ④가 잔존 중복 task 를 success 이전에 cancel" 을 다음으로 대체 — "**중복 호출 빈도 최소화** — ①②③으로 사용자/브로커 유입 중복은 원천 차단. ④ 는 stranded task 발생 빈도를 줄이고(선저장 + PREPARING 복구) 잔존 손실은 수용한다."

5. **§4 부정** (라인 127~131): 첫 항목 "구현 복잡도 — ... + Reconcile cancel 배치, 4곳 모두 정합성 유지" 의 "+ Reconcile cancel 배치" 를 삭제. 셋째 항목 "잔존 리스크 — Tripo cancel 호출이 success 직전에 들어가면 race ..." 전체를 다음으로 대체 — "**잔존 손실** — Tripo POST 성공 직후 `tripo_pending_task` INSERT 또는 TX2 가 실패하면 stranded task 1개 발생, cancel 불가로 **1회분 크레딧 영구 손실**. 발생 빈도는 'POST 성공 × 직후 DB 단발성 장애' 라 낮지만, 발생 시 자동 회수 경로가 없으므로 운영 측 일일 balance 모니터링 + ALERT 로그 + `failure_code=TX2_DB_FAILED` 메트릭으로 사후 인지한다."

6. **§4 검증** (라인 135~139): "과금 추적: `workflow.failure_code=TRIPO_TASK_CANCELLED` 카운터 ..." 줄을 다음으로 대체 — "과금 추적: `failure_code=TX2_DB_FAILED` 카운터 + `tripo.charge.count` 메트릭 + 일일 Tripo balance 모니터링으로 stranded task 발생 빈도를 1주 관측."

7. **하단 새 섹션 추가** — `## 6. 변경 이력`:
   ```
   | 날짜 | 버전 | 내용 |
   |---|---|---|
   | 2026-04-23 | v1.0 | 초안 — 4계층 멱등성 (API key + content_hash + processed_message + Tripo 사후 cancel) |
   | 2026-04-28 | v1.1 | §④ "사후 cancel" 가정 정정 — Tripo `cancel` 엔드포인트 미지원 재확인. ④ 를 "손실 수용 + 운영 모니터링" 으로 재정의. ①②③ 결정은 불변. |
   ```
   상단 `- **상태**: Accepted` 줄 다음에 `- **버전**: v1.1` 한 줄 추가.

**AC (Bash로 표현)**:
```bash
# cancel 이라는 단어가 ADR-011 에 남아있다면 모두 "미지원/불가/금지" 맥락이어야 한다
! grep -n "cancel" docs/architecture/adr/ADR-011-3d-pipeline-multilayer-idempotency.md | grep -v -i -E "미지원|불가|금지|cancelled|TASK_CANCELLED 카운터" | grep -v "^$"
# 변경 이력 섹션 존재 확인
grep -q "^## 6. 변경 이력" docs/architecture/adr/ADR-011-3d-pipeline-multilayer-idempotency.md
```

**금지사항**:
- ①②③ 계층의 결정 문장을 건드리지 마라. 이유: 이번 정정 범위가 ④ 한정이며 ①②③ 은 그대로 유효.
- ADR 상태를 `Superseded` 로 바꾸지 마라. 이유: Q1=A 로 in-place 정정 합의됨.

### Step 2: design-doc-correct

**읽어야 할 파일**:
- `docs/research/2026-04-23-3d-generation-pipeline-design.md` 의 §4, §8.4, §11, §12, §14
- Step 1 산출물: 정정된 ADR-011 (용어 일관성 확보)

**작업**:
설계 doc 의 다음 5개 위치를 정정한다.

1. **§4 표** (라인 ~199): "④ Tripo (사후 정리)" 행의 `식별자` 컬럼 "`tripo_pending_task` 선저장 + Reconcile 중복 task cancel" → "`tripo_pending_task` 선저장 (cancel 불가, 손실 수용)" 으로 변경. `보호 대상` 컬럼 "이중 과금" → "stranded task 빈도 최소화" 로 변경.

2. **§4 ④ Tripo 계층 상세** (라인 ~205~215): 전체를 ADR-011 §2.④ 정정본과 동일 톤으로 재작성. 핵심 변경:
   - "사후 정리 전략" 표현 제거
   - "전략" 항목 3 (cancel 호출) 삭제
   - "리스크 한도" 줄을 "stranded task 발생 시 1회분 크레딧 영구 손실 (cancel 불가). 발생 빈도는 'POST 성공 × 직후 DB 단발성 장애' 라 낮음" 으로 대체

3. **§8.4 의사코드** (라인 ~493~505): `# 중복 task 정리 (별도 배치, 매 5분)` 부터 마지막 `log.warn("duplicate task cancelled", ...)` 까지의 블록 **전체 삭제**. 그 자리에 한 줄 주석으로 대체 — `# stranded task cancel 배치는 미구현 (Tripo cancel API 미지원). 일일 balance 모니터링으로 인지.`

4. **§11 Phase 분할표** (라인 ~604): `**P1-G-γ (보류)**` 행 전체를 다음으로 대체 — `~~**P1-G-γ**~~ Tripo 중복 task cancel 배치 — **❌ 영구 폐기** (Tripo cancel API 미지원, 2026-04-28 확정)` 와 `상태` 컬럼은 ❌.

5. **§12 #1, #2** (라인 ~614~615): #1 의 "결과 / 대체 전략" 컬럼 끝부분 "Reconcile 이 중복 running task 를 cancel 하면 과금 0" 를 "Reconcile 이 PREPARING 복구로 발생 빈도를 줄임. cancel 불가로 잔존 stranded task 는 손실 수용." 로 변경. #2 행 전체를 "✅ 폐기 (2026-04-28) — Tripo cancel API 미지원으로 list-tasks 활용 시나리오 자체가 무효." 로 대체.

6. **§14 변경 이력 표** (라인 ~636): 새 행 추가:
   ```
   | 2026-04-28 | v1.2 | §4 ④ + §8.4 + §11 + §12 의 "사후 cancel" 가정 정정. Tripo cancel API 미지원 재확인 후 ④ 계층 의미를 "stranded task 빈도 최소화 + 손실 수용" 으로 재정의. P1-G-γ 영구 폐기. |
   ```

**AC**:
```bash
# 의사코드의 cancel 블록이 사라졌는지 확인
! grep -n "tripo.cancel\|duplicate task cancelled" docs/research/2026-04-23-3d-generation-pipeline-design.md
# v1.2 변경 이력 추가 확인
grep -q "v1.2" docs/research/2026-04-23-3d-generation-pipeline-design.md
# P1-G-γ 폐기 마킹
grep -q "P1-G-γ.*폐기\|영구 폐기" docs/research/2026-04-23-3d-generation-pipeline-design.md
```

**금지사항**:
- §1~§3 (배경/요구/Tripo 분석), §5~§7 (상태 머신/락/Happy Path), §9 (failure_code 표) 를 건드리지 마라. 이유: 본 정정 범위 외.
- §11 의 다른 Phase 행 (P1-A~P1-F, P1-H, P1-I) 의 상태/내용을 건드리지 마라. 이유: P1-G-γ 외 변경 없음.

### Step 3: javadoc-correct

**읽어야 할 파일**:
- `backend/src/main/java/com/gearshow/backend/showcase/application/service/PrepareWorkflowService.java` 라인 200~225
- Step 1 산출물 (용어 일관성)

**작업**:
`preservePendingTask` 메서드 (라인 ~217) 위의 Javadoc (라인 211~214) 만 정정. **메서드 본문 변경 절대 금지**.

기존:
```java
/**
 * Tripo POST 성공 직후 {@code tripo_pending_task} 레코드를 선저장한다. 실패 시
 * task_id 유실 위험이 있으므로 CRITICAL 로그 + 예외 재전파로 Kafka 재시도를 유도한다.
 * 재시도 시 content_hash dedup 또는 Reconcile 이 중복 task 를 정리한다 (ADR-011 ④).
 *
 * @return 계속 진행 가능하면 {@code true}
 */
```

신규:
```java
/**
 * Tripo POST 성공 직후 {@code tripo_pending_task} 레코드를 선저장한다. 실패 시
 * task_id 유실 위험이 있으므로 CRITICAL 로그 + 예외 재전파로 Kafka 재시도를 유도한다.
 * 재시도 시 {@code transitionToPreparingUnderLock} 가 affected=0 으로 컷되어 두 번째 Tripo POST
 * 는 발생하지 않는다. 다만 이미 발생한 Tripo task 는 task_id 가 유실된 채 백그라운드에서
 * 완료되며, Reconcile.recoverPreparing 이 60초 후 PREPARING stuck 을 markFailed(TX2_DB_FAILED)
 * + ALERT 로 종결한다. **Tripo cancel API 미지원으로 stranded task 자동 회수 불가 — 1회분 크레딧
 * 영구 손실** (운영 측 일일 balance 모니터링으로 인지, ADR-011 §④).
 *
 * @return 계속 진행 가능하면 {@code true}
 */
```

**AC**:
```bash
cd backend
./gradlew compileJava
# 주석에 "중복 task 를 정리한다" 표현이 사라졌는지
! grep -n "중복 task 를 정리한다\|중복 task를 정리한다" src/main/java/com/gearshow/backend/showcase/application/service/PrepareWorkflowService.java
# 새 표현 존재 확인
grep -q "Tripo cancel API 미지원" src/main/java/com/gearshow/backend/showcase/application/service/PrepareWorkflowService.java
```

**금지사항**:
- `preservePendingTask` 메서드 본문 (try/catch/throw) 을 건드리지 마라. 이유: 본 PR 은 docs/comment 만, retry 추가 등 동작 변경은 별도 후속 PR.
- 같은 파일의 다른 메서드 주석을 건드리지 마라. 이유: 본 정정 범위 외.

### Step 4: memory-correct

**읽어야 할 파일**:
- `/Users/opix/.claude/projects/-Users-opix-GearShow/memory/project_3d_pipeline_phase1.md` 라인 36, 55
- `/Users/opix/.claude/projects/-Users-opix-GearShow/memory/project_3d_pipeline_deferred_refactors.md` 라인 129
- Step 1~3 산출물 (용어 일관성)

**작업**:

1. `project_3d_pipeline_phase1.md` 라인 36 ("- P1-G-γ: Tripo 중복 task cancel 배치 (보류 — Tripo list API §12 #2 미정)") 을 다음으로 대체:
   `- ~~P1-G-γ~~: Tripo 중복 task cancel 배치 — **❌ 영구 폐기** (2026-04-28: Tripo cancel API 미지원 재확인. ADR-011 v1.1, design doc v1.2 참조)`

2. 같은 파일 라인 55 ("- ✅ 1/2: Tripo Idempotency-Key 미지원 확인 — 사후 cancel 전략 채택") 를 다음으로 대체:
   `- ✅ 1: Tripo Idempotency-Key 미지원 확인 — `tripo_pending_task` 선저장으로 PREPARING 복구만 가능. cancel API 도 미지원 (2026-04-28) 으로 stranded task 손실 수용.`
   (#1, #2 가 한 줄로 묶여있던 걸 #1 만 남기고 #2 는 다음 줄로 분리)
   바로 다음 줄에 추가:
   `- ✅ 2: Tripo task list API + metadata 필터 — **❌ 폐기** (cancel 미지원으로 list 시나리오 자체 무효, 2026-04-28)`

3. `project_3d_pipeline_deferred_refactors.md` 라인 129 ("- (c) **Reconcile-γ Tripo cancel (P1-G-γ 보류 항목)** — Tripo POST 응답 미수신 구간 ...") 의 (c) 항목 전체를 다음으로 대체:
   `- ~~(c) **Reconcile-γ Tripo cancel (P1-G-γ 보류 항목)**~~ — **❌ 영구 폐기** (2026-04-28: Tripo cancel API 미지원 재확인). 차선책: Tripo balance 일일 모니터링 alert + `failure_code=TX2_DB_FAILED` 카운터 (P1-H 메트릭 항목으로 이관).`

**AC**:
```bash
# 메모리 파일 3곳에 폐기 마킹이 들어갔는지
grep -q "❌ 영구 폐기\|영구 폐기" /Users/opix/.claude/projects/-Users-opix-GearShow/memory/project_3d_pipeline_phase1.md
grep -q "❌ 영구 폐기\|영구 폐기" /Users/opix/.claude/projects/-Users-opix-GearShow/memory/project_3d_pipeline_deferred_refactors.md
# 옛 진술이 잔존하지 않는지 (정확 매칭)
! grep -F "사후 cancel 전략 채택" /Users/opix/.claude/projects/-Users-opix-GearShow/memory/project_3d_pipeline_phase1.md
```

**금지사항**:
- 메모리 파일의 frontmatter (name/description/type/originSessionId) 를 건드리지 마라. 이유: 메모리 인덱싱과 무관한 본문만 정정.
- 다른 메모리 항목을 건드리지 마라. 이유: 본 정정 범위 외.
- `MEMORY.md` 인덱스를 건드리지 마라. 이유: 파일 자체가 추가/삭제되지 않음.

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: `./gradlew compileJava` 통과 (Javadoc 변경만이므로 컴파일 영향 없음).
- **Unhappy Path**: 해당 없음 (테스트 코드 무변경, 신규 동작 없음).
- **추가 검증**: 각 Step 의 AC bash 명령으로 텍스트 정합성 자동 검증.

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

```bash
cd backend
./gradlew compileJava archTest    # docs/comment PR 이라 build 전체 대신 컴파일 + ArchUnit 만
```

각 Step 의 AC 4종이 모두 통과해야 함 (Step 1~4 의 AC 블록 참조).

추가 정성 기준:
- [ ] code-reviewer Critical 지적 0건 (docs PR 이라 사실상 형식 점검)
- [ ] architecture-reviewer Critical 지적 0건 (ADR/설계 일관성 점검)
- [ ] database-optimizer **호출 생략** (스키마/Repository 변경 없음)
- [ ] test-writer **호출 생략** (테스트 변경 없음)
- [ ] EXEC_PLAN 의 Status 필드를 `completed` 로 갱신

## 8. 롤백 전략 (Rollback)

해당 없음. 코드 동작 변경 없음 (Javadoc 1곳만 텍스트 변경). 문서/메모리만 정정. 머지 후 회귀 위험 0.
