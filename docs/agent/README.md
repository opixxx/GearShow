# docs/agent/

하네스 오케스트레이터가 관리하는 에이전트 작업 아티팩트 저장소.

## 디렉토리

- `plans/` — EXEC_PLAN 영구본. `scripts/start-task.sh` 실행 시 자동 생성.
  파일명: `YYYY-MM-DD-<slug>.md`. worktree에서 symlink로 참조됨.
- `trajectories/` — 월별 작업 로그. `YYYY-MM.log`. 각 task의 시작/종료/실패 기록.
- `gap-analysis/` — `/review-gap-analysis` 스킬 실행 결과. 서브에이전트 개선 근거 수집.

## 라이프사이클

- `plans/`, `trajectories/` 는 **영구 보존**. 과거 작업 회고·에이전트 학습 근거.
- `gap-analysis/` 는 월 1회 실행 후 반영된 결과만 보관. 원본은 90일 후 아카이브.

## 참조

- 파이프라인 전체: `.claude/skills/orchestrator/SKILL.md`
- 트레젝토리 포맷: `scripts/start-task.sh` 참조
