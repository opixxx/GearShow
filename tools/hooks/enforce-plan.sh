#!/bin/bash
# tools/hooks/enforce-plan.sh  (PreToolUse: Edit|Write)
#
# EXEC_PLAN.md가 없거나 <TODO:...> 마커가 남은 상태에서 소스 코드 편집을 차단한다.
# "계획 없이 코드 없음" 규칙을 물리적으로 강제한다.
#
# 코드 파일로 간주하는 확장자: .java, .kts, .gradle, .yml, .yaml, .properties, .sql
# 그 외(문서, 설정, 스크립트)는 이 훅의 대상이 아니다.

set -e

input=$(cat)
file_path=$(echo "$input" | jq -r '.tool_input.file_path // empty')

if [ -z "$file_path" ]; then
  exit 0
fi

# ─── 코드 파일 여부 판별 ─────────────────────────────────────────────────────
case "$file_path" in
  *.java|*.kts|*.gradle|*.yml|*.yaml|*.properties|*.sql) ;;
  *) exit 0 ;;
esac

# ─── worktree 루트 찾기 ──────────────────────────────────────────────────────
dir=$(dirname "$file_path")
top=$(git -C "$dir" rev-parse --show-toplevel 2>/dev/null || true)

if [ -z "$top" ]; then
  exit 0
fi

# 메인 작업 디렉토리(linked worktree 아님)는 enforce-worktree.sh가 처리
# 이 훅은 linked worktree 내부에서만 동작한다
if [ ! -f "$top/.git" ]; then
  exit 0
fi

# ─── EXEC_PLAN.md 검사 ───────────────────────────────────────────────────────
plan="$top/EXEC_PLAN.md"

if [ ! -L "$plan" ] && [ ! -f "$plan" ]; then
  reason="🚫 계획 없이 코드 편집 금지.

이 worktree에 EXEC_PLAN.md가 없습니다. 다음 명령으로 작업을 올바르게 시작하세요:

  bash scripts/start-task.sh <task-name> <type>

기존 작업을 재개하는 중이라면 worktree가 잘못 생성되었을 수 있습니다. 정리 후 재시작하세요."
  jq -n --arg r "$reason" '{decision:"block", reason:$r}'
  exit 0
fi

# symlink를 따라가서 실제 파일 검사
if grep -q '<TODO:' "$plan" 2>/dev/null; then
  unfilled=$(grep -c '<TODO:' "$plan" || true)
  reason="🚫 EXEC_PLAN에 <TODO:...> 마커가 ${unfilled}개 남아있습니다.

먼저 다음 섹션들을 모두 채워야 코드 편집이 허용됩니다:
  - 목표 (Goal)
  - 범위 In/Out (Scope)
  - 변경 대상 (Affected)
  - 접근 (Approach)
  - 단계 (Steps)
  - 테스트 계획 (Test Plan)
  - 위험도 (Risk)
  - 롤백 전략 (Rollback) — 해당 없으면 '해당 없음' 기입

플랜 파일: ${plan}"
  jq -n --arg r "$reason" '{decision:"block", reason:$r}'
  exit 0
fi

exit 0
