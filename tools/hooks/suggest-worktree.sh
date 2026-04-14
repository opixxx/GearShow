#!/bin/bash
# tools/hooks/suggest-worktree.sh  (UserPromptSubmit)
#
# 사용자가 구현성 프롬프트를 입력했을 때, 현재 위치가 메인 작업 디렉토리의 main/master
# 브랜치라면 worktree 생성을 사전 안내한다.
#
# 에이전트가 Edit를 시도해서 enforce-worktree.sh에 차단당하기 전에, 프롬프트 단계에서
# 먼저 "worktree부터 만들어야 한다"를 인지시킨다.

set -e

input=$(cat)
prompt=$(echo "$input" | jq -r '.prompt // empty')

if [ -z "$prompt" ]; then
  exit 0
fi

# ─── 구현성 프롬프트 감지 ────────────────────────────────────────────────────
# 키워드가 하나라도 포함되면 "구현성" 으로 간주
if ! echo "$prompt" | grep -qiE '구현|만들어|추가|수정|고쳐|리팩|버그|기능|api|엔드포인트|implement|add|refactor|fix|feature'; then
  exit 0
fi

# ─── 현재 위치 상태 확인 ─────────────────────────────────────────────────────
project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
top=$(git -C "$project_dir" rev-parse --show-toplevel 2>/dev/null || true)

if [ -z "$top" ]; then
  exit 0
fi

# linked worktree이면 이미 올바른 위치 → 안내 불필요
if [ -f "$top/.git" ]; then
  exit 0
fi

# 메인 디렉토리이고 main/master 브랜치면 안내 주입
branch=$(git -C "$top" branch --show-current)
if [ "$branch" = "main" ] || [ "$branch" = "master" ]; then
  context="⚠️  현재 ${branch} 브랜치의 메인 작업 디렉토리입니다.

이 요청은 구현성 작업으로 보이므로 worktree에서 진행해야 합니다. 다음 순서를 따르세요:

1. 사용자에게 적절한 task-name과 type(feature/fix/refactor/chore/docs)을 간단히 확인
2. \`bash scripts/start-task.sh <task-name> <type>\` 실행
3. 생성된 worktree로 이동
4. EXEC_PLAN.md의 <TODO:...> 섹션을 채운 뒤 구현 시작

이 단계를 건너뛰고 Edit/Write를 시도하면 enforce-worktree.sh 훅이 차단합니다."

  jq -n --arg c "$context" '{hookSpecificOutput:{hookEventName:"UserPromptSubmit", additionalContext:$c}}'
  exit 0
fi

exit 0
