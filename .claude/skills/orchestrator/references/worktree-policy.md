# Worktree 정책

## 원칙
> **`main`/`master` 브랜치의 메인 작업 디렉토리에서 소스 코드 직접 수정 금지.**
> 모든 코드 변경은 linked worktree 위에서 이루어진다.

## 판별 방법
- `.git`이 **파일** → linked worktree (허용)
- `.git`이 **디렉토리** → 메인 작업 디렉토리 (소스 수정 차단)

## 절차

작업 시작 시 반드시 다음 명령으로 시작한다:

```bash
bash scripts/start-task.sh <task-name> <type>
```

이 스크립트가:
1. worktree 생성 (`../gearshow-<slug>`)
2. 브랜치 생성 (`<type>/<slug>`)
3. 빈 포트 할당 (9000~9099)
4. EXEC_PLAN 영구본 생성 + worktree symlink
5. `.task/` 메타 파일
6. `logs/` 디렉토리
7. 트레젝토리 기록 시작

작업 완료 후:
```bash
git worktree remove ../gearshow-<slug>
```

## 브랜치 타입
- `feature/*` — 새 기능
- `fix/*` — 버그 수정
- `refactor/*` — 리팩토링 (동작 변경 없음)
- `chore/*` — 빌드·설정 변경
- `docs/*` — 문서만 변경

## 예외 (메인 디렉토리에서도 허용)

| 경로 | 이유 |
|---|---|
| `docs/**`, `*.md` | 문서 수정은 worktree 오버헤드 큼 |
| `CLAUDE.md`, `AGENTS.md`, `AGENT.md` | 하네스 자체 수정은 즉시 반영 필요 |
| `.claude/**` | 하네스 설정 |
| `tools/hooks/**`, `tools/templates/**` | 하네스 스크립트 |
| `scripts/start-task.sh` | 진입점 스크립트 |
| `.github/**` | CI 파이프라인 |
| `.githooks/**` | 로컬 git 훅 |
| `.gitignore`, `.gitattributes`, `.editorconfig` | 레포 설정 |

그 외 모든 소스 (`backend/src/**`, `docker/**`, `build.gradle` 등)는 linked worktree 필수.

## 강제 메커니즘

| 훅 | 타이밍 | 역할 |
|---|---|---|
| `suggest-worktree.sh` | UserPromptSubmit | 구현성 프롬프트 감지 시 worktree 생성 안내 |
| `enforce-worktree.sh` | PreToolUse (Edit/Write) | main/master + 메인 디렉토리에서 소스 편집 차단 |
| `enforce-plan.sh` | PreToolUse (Edit/Write) | EXEC_PLAN.md 없거나 `<TODO:>` 남은 상태에서 편집 차단 |
| `verify-and-block.sh` | Stop | 작업 종료 시 compile + archTest 실행, 실패 시 자가수정 강제 |

## 병렬 작업
여러 task를 동시에 진행할 수 있다. 각 worktree는:
- 독립된 포트 사용 (`.task/port` 참조)
- 독립된 `logs/` 디렉토리
- 독립된 자가수정 카운터 (`/tmp/gearshow-selfheal/<worktree-slug>`)
