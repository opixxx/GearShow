#!/bin/bash
# scripts/start-task.sh <task-name> <type> [--with-context [N]]
#
# 새 작업 시작 시 worktree · EXEC_PLAN · 포트 · 로그를 원자적으로 셋업한다.
# 이 스크립트가 에이전트 작업의 단일 진입점이며, 하나라도 실패하면 전체 롤백된다.
#
# 옵션:
#   --with-context [N]  최근 N개 트레젝토리 항목을 EXEC_PLAN 부록으로 첨부 (기본 5)
#                       이전 작업의 패턴/마찰점을 새 작업 컨텍스트로 활용
#
# 사용 예:
#   bash scripts/start-task.sh showcase-failure-notification feature
#   bash scripts/start-task.sh price-validation fix --with-context
#   bash scripts/start-task.sh user-phone-auth feature --with-context 10

set -euo pipefail

# ─── 인자 검증 ────────────────────────────────────────────────────────────────
TASK_NAME="${1:-}"
TYPE="${2:-}"
WITH_CONTEXT=false
CONTEXT_N=5

# 옵션 파싱 (3번째 이후 인자)
shift 2 2>/dev/null || true
while [ $# -gt 0 ]; do
  case "$1" in
    --with-context)
      WITH_CONTEXT=true
      # 다음 인자가 숫자면 N으로 사용
      if [ -n "${2:-}" ] && [[ "${2:-}" =~ ^[0-9]+$ ]]; then
        CONTEXT_N="$2"
        shift
      fi
      shift
      ;;
    *)
      echo "❌ 알 수 없는 옵션: $1"; exit 1
      ;;
  esac
done

if [ -z "$TASK_NAME" ] || [ -z "$TYPE" ]; then
  cat <<EOF
사용법: bash scripts/start-task.sh <task-name> <type> [--with-context [N]]

  task-name : 작업 식별자 (한글·공백 허용, 자동 슬러그화됨)
  type      : feature | fix | refactor | chore | docs
  --with-context [N] : 최근 N개 트레젝토리를 EXEC_PLAN 부록에 첨부 (기본 5)

예시:
  bash scripts/start-task.sh 쇼케이스-실패-알림 feature
  bash scripts/start-task.sh model-retry-count fix
  bash scripts/start-task.sh user-phone-auth feature --with-context 10
EOF
  exit 1
fi

case "$TYPE" in
  feature|fix|refactor|chore|docs) ;;
  *) echo "❌ type은 feature|fix|refactor|chore|docs 중 하나여야 합니다."; exit 1 ;;
esac

# ─── 슬러그화 & 경로 계산 ─────────────────────────────────────────────────────
# 한글·공백 → 하이픈, 소문자만 유지
SLUG=$(echo "$TASK_NAME" | tr '[:upper:] ' '[:lower:]-' | tr -cd 'a-z0-9-' | sed 's/--*/-/g' | sed 's/^-//;s/-$//')
if [ -z "$SLUG" ]; then
  # 한글만 입력되어 슬러그가 비면 타임스탬프로 대체
  SLUG="task-$(date +%Y%m%d-%H%M%S)"
fi

BRANCH="${TYPE}/${SLUG}"
REPO_ROOT="$(git rev-parse --show-toplevel)"
WT_PATH="${REPO_ROOT}/../gearshow-${SLUG}"
PLAN_DATE="$(date +%Y-%m-%d)"
PLAN_PATH="${REPO_ROOT}/docs/agent/plans/${PLAN_DATE}-${SLUG}.md"

# ─── 중복 검사 ────────────────────────────────────────────────────────────────
if [ -e "$WT_PATH" ]; then
  echo "❌ worktree 경로가 이미 존재합니다: $WT_PATH"
  echo "   기존 작업을 정리하려면: git worktree remove $WT_PATH"
  exit 1
fi

if git -C "$REPO_ROOT" show-ref --verify --quiet "refs/heads/$BRANCH"; then
  echo "❌ 브랜치가 이미 존재합니다: $BRANCH"
  exit 1
fi

if [ -e "$PLAN_PATH" ]; then
  echo "❌ 동일 이름의 플랜 파일이 이미 존재합니다: $PLAN_PATH"
  exit 1
fi

# ─── 빈 포트 할당 (9000~9099) ────────────────────────────────────────────────
find_free_port() {
  for p in $(seq 9000 9099); do
    if ! lsof -iTCP:"$p" -sTCP:LISTEN -n >/dev/null 2>&1; then
      echo "$p"
      return 0
    fi
  done
  echo "❌ 9000~9099 범위에 빈 포트 없음" >&2
  exit 1
}
PORT=$(find_free_port)

# ─── 템플릿 존재 확인 ─────────────────────────────────────────────────────────
TEMPLATE="${REPO_ROOT}/tools/templates/EXEC_PLAN.template.md"
if [ ! -f "$TEMPLATE" ]; then
  echo "❌ EXEC_PLAN 템플릿을 찾을 수 없습니다: $TEMPLATE"
  exit 1
fi

# ─── 1) worktree 생성 ─────────────────────────────────────────────────────────
# 기본 브랜치(main/master) 기준으로 분기하여 현재 HEAD의 미완성 작업이 섞이지 않도록 한다
# origin/main 이 존재하면 그걸, 없으면 로컬 main → master 순으로 fallback
BASE_REF=""
for candidate in origin/main origin/master main master; do
  if git -C "$REPO_ROOT" rev-parse --verify "$candidate" >/dev/null 2>&1; then
    BASE_REF="$candidate"
    break
  fi
done
if [ -z "$BASE_REF" ]; then
  echo "❌ 기본 브랜치(main/master)를 찾을 수 없습니다."
  exit 1
fi

git -C "$REPO_ROOT" worktree add "$WT_PATH" -b "$BRANCH" "$BASE_REF"

# ─── 2) EXEC_PLAN 영구본 생성 + worktree에 symlink ────────────────────────────
mkdir -p "${REPO_ROOT}/docs/agent/plans"
# macOS/Linux 호환 sed 치환 — 구분자를 | 로 사용하여 경로 충돌 회피
sed \
  -e "s|{{TASK}}|${TASK_NAME}|g" \
  -e "s|{{TYPE}}|${TYPE}|g" \
  -e "s|{{BRANCH}}|${BRANCH}|g" \
  -e "s|{{WT}}|${WT_PATH}|g" \
  -e "s|{{PORT}}|${PORT}|g" \
  -e "s|{{DATE}}|$(date '+%Y-%m-%d %H:%M')|g" \
  "$TEMPLATE" > "$PLAN_PATH"

# worktree 루트에 symlink — PR diff에 계획이 포함되어 리뷰 시 함께 보인다
ln -s "$PLAN_PATH" "${WT_PATH}/EXEC_PLAN.md"

# ─── 2-1) --with-context 옵션 처리: 최근 트레젝토리 부록 첨부 ─────────────────
if [ "$WITH_CONTEXT" = "true" ]; then
  TRAJ_DIR="${REPO_ROOT}/docs/agent/trajectories"
  if [ -d "$TRAJ_DIR" ]; then
    # 모든 월별 로그를 합쳐서 최근 N건 추출 (역순)
    RECENT=$(cat "$TRAJ_DIR"/*.log 2>/dev/null | tail -"$CONTEXT_N" || true)
    if [ -n "$RECENT" ]; then
      cat >> "$PLAN_PATH" <<EOF

---

## 부록 — 최근 ${CONTEXT_N}개 트레젝토리 (참고용 컨텍스트)

> 이전 작업의 패턴, 마찰점, 학습을 새 작업 시작 시 참고하기 위해 자동 첨부됨.
> --with-context 옵션으로 활성화. 무시해도 무방하나 유사한 작업이면 큰 도움.

\`\`\`
${RECENT}
\`\`\`
EOF
    fi
  fi
fi

# ─── 3) 메타 파일 ─────────────────────────────────────────────────────────────
mkdir -p "${WT_PATH}/.task"
echo "$PORT"      > "${WT_PATH}/.task/port"
echo "$BRANCH"    > "${WT_PATH}/.task/branch"
echo "$PLAN_PATH" > "${WT_PATH}/.task/plan"
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${WT_PATH}/.task/started-at"

# ─── 4) 로그 디렉토리 ─────────────────────────────────────────────────────────
mkdir -p "${WT_PATH}/logs"

# ─── 5) 트레젝토리 기록 시작 ──────────────────────────────────────────────────
TRAJ_DIR="${REPO_ROOT}/docs/agent/trajectories"
mkdir -p "$TRAJ_DIR"
TRAJ_FILE="${TRAJ_DIR}/$(date +%Y-%m).log"
echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] STARTED task=${SLUG} type=${TYPE} branch=${BRANCH} port=${PORT} plan=${PLAN_PATH}" \
  >> "$TRAJ_FILE"

# ─── 6) 안내 ──────────────────────────────────────────────────────────────────
cat <<EOF

✅ 작업 셋업 완료

  Task     : ${TASK_NAME}
  Type     : ${TYPE}
  Branch   : ${BRANCH}
  Worktree : ${WT_PATH}
  Port     : ${PORT}
  Plan     : ${PLAN_PATH}

다음 단계:
  1) cd ${WT_PATH}
  2) EXEC_PLAN.md의 <TODO:...> 마커를 모두 채운다
     (목표·범위·단계·테스트·완료 기준·위험도)
  3) 계획이 완성되어야 코드 편집이 허용됩니다 (enforce-plan.sh 훅)

작업 종료 시:
  git worktree remove ${WT_PATH}

EOF
