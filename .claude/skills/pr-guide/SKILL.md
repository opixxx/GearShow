---
name: pr
description: PR을 생성한다. 테스트 통과 → 커버리지 검증 → PR 생성 절차를 자동으로 수행한다.
user_invocable: true
---

# GearShow PR 생성

이 스킬은 GitHub Pull Request를 생성하는 전체 절차를 수행한다.

## 실행 절차

아래 단계를 순서대로 수행한다. 테스트가 실패하면 **절대 PR을 생성하지 않는다.**

---

### Step 1: 사전 조건 확인

1. `gh` CLI 설치 및 인증 확인

```bash
gh auth status
```

2. 현재 브랜치가 `main`이 아닌지 확인

```bash
git branch --show-current
```

3. 커밋되지 않은 변경사항 확인

```bash
git status
```

커밋되지 않은 변경이 있으면 사용자에게 커밋 여부를 확인한다.

---

### Step 2: 테스트 실행 [필수]

> **모든 테스트가 통과해야 PR을 생성할 수 있다.**

```bash
cd backend && ./gradlew build
```

`./gradlew build`는 컴파일 + 전체 테스트 + JaCoCo 커버리지 검증을 한 번에 수행한다.

#### 테스트 우선순위

| 우선순위 | 테스트 유형 | 위치 | 필수 |
|:-----:|:----------|:----|:---:|
| 1 | **Cucumber 인수 테스트** | `src/test/resources/features/` | **필수** |
| 2 | **통합 테스트** | 각 도메인 `adapter/`, `application/` 패키지 | **필수** |
| 3 | 단위 테스트 | 각 도메인 `domain/` 패키지 | 있을 경우 필수 |

#### 통합 테스트 확인 사항

| 대상 | 필수 여부 | 확인 내용 |
|:----|:-------:|:---------|
| Persistence Adapter | **필수** | JPA 매핑, 쿼리 정합성, Mapper 변환 |
| Service (UseCase) | 복잡한 경우 필수 | 유스케이스 흐름, 트랜잭션, 도메인 규칙 |
| Controller | 권장 | 요청/응답 직렬화, Validation, Security |

> 신규/변경된 Adapter는 반드시 통합 테스트가 있어야 한다.

#### 커버리지 기준

> **JaCoCo 인스트럭션 커버리지 70% 이상 필수**

아직 UseCase가 구현되지 않은 도메인은 `build.gradle`에서 JaCoCo 측정 제외 설정으로 관리한다. 도메인 UseCase가 구현되면 해당 제외 항목을 제거하여 범위를 확장한다.

#### 테스트 실패 시

1. 실패한 테스트 확인 (인수 → 통합 → 단위 순서)
2. 코드 수정 (기존 기능이 깨지지 않도록)
3. 재실행: `./gradlew build`
4. **모든 테스트 통과 후에만 다음 단계 진행**

---

### Step 3: 컨텍스트 수집

병렬로 실행한다:

```bash
# 현재 브랜치
git branch --show-current

# PR에 포함될 커밋
git log origin/main..HEAD --oneline --no-decorate

# 변경된 파일
git diff origin/main..HEAD --stat

# 베이스 브랜치
git remote show origin | grep "HEAD branch"
```

---

### Step 4: Push

모든 커밋이 리모트에 push되어 있는지 확인한다.

```bash
git push origin HEAD
```

---

### Step 5: PR 생성

커밋 내용을 분석하여 PR 제목과 본문을 작성한다.

#### 브랜치 접두사 → PR 제목

| 접두사 | 커밋 접두사 | 예시 |
|:------|:---------|:----|
| `feature/` | `feat:` | `feat: 쇼케이스 등록 API 구현` |
| `fix/` | `fix:` | `fix: 카카오 로그인 토큰 갱신 오류 수정` |
| `refactor/` | `refactor:` | `refactor: User 도메인 모델 개선` |
| `ci/` | `ci:` | `ci: JaCoCo 커버리지 기준 상향` |
| `docs/` | `docs:` | `docs: API 명세 업데이트` |

#### PR 본문 형식

```bash
gh pr create --title "PR 제목" --body "$(cat <<'EOF'
## Summary
- 변경 사항 요약 (1~3줄)

## Changes
- 구체적인 변경 내용
- 파일/패키지 단위로 정리

## Test
- [ ] Cucumber 인수 테스트 통과
- [ ] 통합 테스트 통과 (Adapter, Service)
- [ ] JaCoCo 커버리지 70% 이상
- [ ] 신규 시나리오/통합 테스트 추가 (있는 경우)
- [ ] 기존 테스트 영향 없음

## Notes
- 리뷰어가 알아야 할 사항
- 후속 작업 계획

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" --base main
```

---

### Step 6: PR 생성 후

1. **PR URL 출력** — 사용자가 확인할 수 있도록
2. **CI 확인 안내** — GitHub Actions 빌드/테스트 자동 실행됨
3. **후속 작업 안내**:
   - 리뷰어 추가: `gh pr edit --add-reviewer USERNAME`
   - 라벨 추가: `gh pr edit --add-label "feature"`

---

## 에러 대응

| 상황 | 대응 |
|:----|:----|
| main 대비 커밋 없음 | 다른 브랜치에서 작업했는지 확인 |
| 브랜치 미push | `git push -u origin HEAD` 실행 |
| PR 이미 존재 | `gh pr view`로 확인 후 업데이트 여부 결정 |
| 머지 충돌 | 충돌 해결 후 rebase |

---

## 최종 체크리스트

- [ ] `gh` CLI 설치 및 인증 완료
- [ ] **Cucumber 인수 테스트 통과**
- [ ] **통합 테스트 통과** (Persistence Adapter, Service, Controller)
- [ ] 단위 테스트 통과 (있는 경우)
- [ ] 신규/변경된 Adapter에 통합 테스트가 있다
- [ ] **JaCoCo 커버리지 70% 이상**
- [ ] 구현 완료된 도메인은 JaCoCo 제외 목록에서 제거했다
- [ ] 작업 디렉토리 클린
- [ ] 모든 커밋이 push됨
- [ ] 베이스 브랜치(main) 기준 최신 상태
- [ ] PR 제목이 변경 내용을 명확히 반영
- [ ] 보안 민감 정보 미포함 (.env, API 키 등)
