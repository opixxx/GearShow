# 에스컬레이션 기준 + 종료 상태

## 🚦 종료 상태 (3가지)

작업은 반드시 다음 3가지 중 하나의 명시적 상태로 마무리한다.
EXEC_PLAN 상단의 `Status` 필드와 트레젝토리 로그에 동일한 값을 기록한다.

| 상태 | 의미 | 다음 동작 |
|---|---|---|
| ✅ `completed` | 모든 step 통과 + 검증 완료 | 트레젝토리에 완료 라인 추가, PR 생성, worktree 정리 |
| ❌ `error` | 자가수정 한계 초과 또는 복구 불가 에러 | 사용자에게 보고, EXEC_PLAN의 `error_message` 채움, 작업 정지 |
| ⏸️ `blocked` | 사용자 개입 필요 (외부 자원·결정·권한·인증 등) | **즉시 정지**, `blocked_reason` 명시, 다음 진행 보류 |

`blocked`는 "에이전트가 더 할 수 있는데 안 하는" 상태가 아니다. **사용자만이 풀 수 있는 매듭**일 때만 사용한다.
모호하면 `error`로.

### blocked vs error 판별

| 상황 | 상태 |
|---|---|
| API 키가 없음 (사용자가 발급해야) | `blocked` |
| 외부 시스템 접근 권한 부족 | `blocked` |
| Breaking change 결정이 필요 | `blocked` |
| 데이터 마이그레이션 계획 승인 필요 | `blocked` |
| 컴파일 에러를 3번 시도해도 못 고침 | `error` |
| 테스트가 계속 실패 | `error` |
| ArchUnit 위반을 자가수정 못함 | `error` |
| 의존 라이브러리가 손상 | `error` |

### Status 전환

```
pending → in_progress → completed
                     ↘ error
                     ↘ blocked → (사용자 해결 후) → in_progress → completed
```

`blocked` 상태에서 사용자가 막힘을 풀어주면 `in_progress`로 다시 전환하고 작업 재개.

---

## 자동 차단 조건 (훅이 감지 → `error`)

- [ ] 자가수정 3회 연속 실패 (`verify-and-block.sh`)
- [ ] `domain/` 패키지에 Spring/JPA import (ArchUnit)
- [ ] main/master 브랜치 메인 디렉토리에서 소스 편집 시도 (`enforce-worktree.sh`)
- [ ] EXEC_PLAN 없이 코드 편집 시도 (`enforce-plan.sh`)
- [ ] 위험 Bash 명령 시도 (`guard-bash.sh`) — 예: `rm -rf /`, `git push --force`, `DROP TABLE`

---

## 수동 판단 조건 (에이전트가 감지 → `blocked`)

다음은 **사용자 결정 없이는 진행 불가**한 항목들. 발견 즉시 작업 멈추고 보고:

- [ ] DB 마이그레이션 스크립트가 필요한 변경
- [ ] 공개 API Breaking Change 발견
- [ ] Kafka 이벤트 계약 변경
- [ ] 보안 경계 변경 (인증·인가·PII 처리 방식)
- [ ] 외부 서비스 신규 연동 (PG, SMS, FCM 등) — 벤더 선택·계정 발급 필요
- [ ] 새 환경변수·시크릿 필요
- [ ] EXEC_PLAN과 실제 구현 diff 150% 초과 (scope creep — 계획 재합의 필요)
- [ ] Bounded Context 경계를 넘나드는 예기치 못한 결합 발견

---

## 보고 양식

### `blocked` 보고
```
⏸️ Status: blocked

- 차단 사유 : <위 체크리스트 중 해당 항목>
- 발견 시점 : <Phase X 진행 중, Step N>
- 사용자가 풀어야 할 것 :
    1. <구체 결정/자원 1>
    2. <구체 결정/자원 2>
- 작업 재개 조건 : <어떤 입력이 있어야 in_progress로 전환되는지>
- 임시 보존 위치 : <중간 산출물이 어디 있는지>
```

### `error` 보고
```
❌ Status: error

- 에러 유형 : <컴파일 | archTest | 테스트 | 기타>
- 자가수정 시도 : N/3
- 마지막 에러 메시지 : <stderr 발췌>
- 시도한 수정 내역 : <간단 요약>
- 추정 원인 : <에이전트의 1차 진단>
- 다음 진단 권장 : <사용자가 확인할 만한 것>
```

### `completed` 마무리
```
✅ Status: completed

- 변경 범위 : <파일 N개, +X / -Y>
- 통과 검증 : <Bash 커맨드 결과 요약>
- 서브에이전트 지적 : Critical 0 / Major N / Minor M
- 트레젝토리 기록 : docs/agent/trajectories/YYYY-MM.log
- 다음 단계 : <PR 생성 / 후속 작업 / 정리>
```

---

## 금지 사항

- 에스컬레이션 조건 충족인데 "조금만 더 해보자"며 진행
- 사용자에게 알리지 않고 scope를 임의 축소하여 회피
- 영향 범위를 조용히 확장하여 "다른 것도 고쳤습니다" 보고
- `blocked`로 멈춰야 할 상황을 `error`로 위장 (책임 회피)
- `error`로 멈춰야 할 상황을 `blocked`로 위장 (사용자 떠넘기기)
