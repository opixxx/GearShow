#!/bin/bash
# tools/hooks/enforce-worktree.sh  (PreToolUse: Edit|Write|NotebookEdit)
#
# main/master 브랜치의 메인 작업 디렉토리에서 소스 코드 직접 수정을 차단한다.
# 문서·하네스 설정·CI 파일은 예외로 허용한다.
#
# 판별 원리:
#   - linked worktree인 경우 `.git`이 파일, 메인 디렉토리인 경우 `.git`이 디렉토리
#
# 에이전트에게 차단 사유와 복구 명령을 함께 전달하여 자체 수정을 유도한다.

set -e

input=$(cat)
file_path=$(echo "$input" | jq -r '.tool_input.file_path // empty')

# 파일 경로 없음 → 간섭하지 않음
if [ -z "$file_path" ]; then
  exit 0
fi

# ─── 예외 화이트리스트 ────────────────────────────────────────────────────────
# main 디렉토리에서도 수정을 허용하는 경로
case "$file_path" in
  */docs/*|*.md|\
  */CLAUDE.md|*/AGENTS.md|*/AGENT.md|\
  */.claude/*|*/tools/hooks/*|*/tools/templates/*|\
  */scripts/start-task.sh|\
  */.github/*|*/.githooks/*|\
  */\.gitignore|*/\.gitattributes|*/\.editorconfig)
    exit 0
    ;;
esac

# ─── git 상태 확인 ────────────────────────────────────────────────────────────
dir=$(dirname "$file_path")
top=$(git -C "$dir" rev-parse --show-toplevel 2>/dev/null || true)

# git 레포 밖 → 간섭하지 않음
if [ -z "$top" ]; then
  exit 0
fi

# linked worktree인 경우 `.git`이 파일 → 허용
if [ -f "$top/.git" ]; then
  exit 0
fi

# 메인 작업 디렉토리에서 main/master 브랜치인 경우 차단
branch=$(git -C "$top" branch --show-current)
if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
  task_hint=$(basename "$file_path" .java | sed 's/\..*//' | tr '[:upper:]' '[:lower:]')
  reason="🚫 보호 규칙 위반: ${branch} 브랜치의 메인 작업 디렉토리에서 소스 코드 직접 수정은 금지됩니다.

다음 명령으로 worktree를 생성한 뒤 해당 경로에서 작업을 재개하세요:

  bash scripts/start-task.sh <task-name> <type>

예:
  bash scripts/start-task.sh ${task_hint} feature

이 스크립트는 worktree · EXEC_PLAN · 포트 · 로그 디렉토리를 원자적으로 생성합니다.
EXEC_PLAN의 <TODO:...> 섹션을 채운 뒤에만 코드 편집이 허용됩니다."

  jq -n --arg r "$reason" '{decision:"block", reason:$r}'
  exit 0
fi

exit 0
