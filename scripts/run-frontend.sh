#!/bin/bash
# scripts/run-frontend.sh [--env=dev|prod] [--device=<id>] [--user=<id>] [--no-simulator] [-h|--help]
#
# 프론트엔드(Flutter) 실행 단일 진입점. iOS Simulator 부팅 후 frontend/ 에서
# `flutter run --dart-define=ENV=<env>` 를 실행한다. 매번 시뮬레이터 켜고 디렉토리
# 이동하고 dart-define 옵션을 떠올리는 번거로움을 제거한다.
#
# 옵션:
#   --env=dev|prod    실행 환경 (default: prod). dev 면 카카오 SDK 초기화 스킵.
#   --device=<id>     flutter device id 명시 (default: flutter 자동 선택)
#   --user=<id>       DEV_USER_ID 주입 (--env=dev 와 함께만 의미, 그 외엔 경고 후 무시)
#   --no-simulator    iOS Simulator 자동 부팅 스킵 (Android emulator/Chrome 등 사용 시)
#   -h, --help        이 도움말 출력
#
# 환경변수 (셋되어 있으면 dart-define 으로 자동 전달):
#   KAKAO_NATIVE_APP_KEY        카카오 네이티브 앱 키
#   KAKAO_JAVASCRIPT_APP_KEY    카카오 JavaScript 앱 키
#
# 사용 예:
#   bash scripts/run-frontend.sh                          # prod, iOS Simulator 자동 부팅
#   bash scripts/run-frontend.sh --env=dev --user=1       # dev 모드 + 테스트 사용자 1번
#   bash scripts/run-frontend.sh --no-simulator -d chrome # Chrome 으로 띄움 (시뮬 스킵)

set -euo pipefail

# ─── 사용법 ──────────────────────────────────────────────────────────────────
# 헤더 주석 블록만 출력 — `#!/bin/bash` 는 건너뛰고, 첫 비-주석 라인 직전까지.
# 헤더 길이가 늘어나도 라인 번호 의존 없이 동작한다.
usage() {
    awk 'NR==1 && /^#!/ { next }
         /^#/ { sub(/^# ?/, ""); print; next }
         { exit }' "$0"
}

# ─── 인자 파싱 ───────────────────────────────────────────────────────────────
ENV_VALUE="prod"
DEVICE=""
DEV_USER_ID=""
LAUNCH_SIMULATOR=true

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env=*)
            ENV_VALUE="${1#*=}"
            shift
            ;;
        --env)
            ENV_VALUE="${2:-}"
            shift 2
            ;;
        --device=*|-d=*)
            DEVICE="${1#*=}"
            shift
            ;;
        --device|-d)
            DEVICE="${2:-}"
            shift 2
            ;;
        --user=*)
            DEV_USER_ID="${1#*=}"
            shift
            ;;
        --user)
            DEV_USER_ID="${2:-}"
            shift 2
            ;;
        --no-simulator)
            LAUNCH_SIMULATOR=false
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "오류: 알 수 없는 옵션 '$1'" >&2
            echo >&2
            usage >&2
            exit 1
            ;;
    esac
done

# ─── 검증 ────────────────────────────────────────────────────────────────────
if [[ "$ENV_VALUE" != "dev" && "$ENV_VALUE" != "prod" ]]; then
    echo "오류: --env 는 dev 또는 prod 만 허용한다 (입력값: '$ENV_VALUE')" >&2
    exit 1
fi

if [[ -n "$DEV_USER_ID" && "$ENV_VALUE" != "dev" ]]; then
    echo "경고: --user 는 --env=dev 일 때만 적용된다. 무시함." >&2
    DEV_USER_ID=""
fi

# ─── 경로 계산 (스크립트 위치 기준) ──────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"

if [[ ! -f "$FRONTEND_DIR/pubspec.yaml" ]]; then
    echo "오류: $FRONTEND_DIR/pubspec.yaml 이 없습니다. Flutter 프로젝트 위치를 확인하세요." >&2
    exit 1
fi

# ─── iOS Simulator 부팅 ──────────────────────────────────────────────────────
if [[ "$LAUNCH_SIMULATOR" == true ]]; then
    echo "[run-frontend] iOS Simulator 부팅 (open -a Simulator)..."
    open -a Simulator
fi

# ─── flutter run 명령 조립 ──────────────────────────────────────────────────
FLUTTER_ARGS=("run" "--dart-define=ENV=$ENV_VALUE")

if [[ -n "$DEVICE" ]]; then
    FLUTTER_ARGS+=("-d" "$DEVICE")
fi

if [[ -n "$DEV_USER_ID" ]]; then
    FLUTTER_ARGS+=("--dart-define=DEV_USER_ID=$DEV_USER_ID")
fi

# 카카오 키는 환경변수에서만 읽음 (시크릿 하드코딩 금지)
if [[ -n "${KAKAO_NATIVE_APP_KEY:-}" ]]; then
    FLUTTER_ARGS+=("--dart-define=KAKAO_NATIVE_APP_KEY=$KAKAO_NATIVE_APP_KEY")
fi
if [[ -n "${KAKAO_JAVASCRIPT_APP_KEY:-}" ]]; then
    FLUTTER_ARGS+=("--dart-define=KAKAO_JAVASCRIPT_APP_KEY=$KAKAO_JAVASCRIPT_APP_KEY")
fi

# ─── 실행 ────────────────────────────────────────────────────────────────────
echo "[run-frontend] cd $FRONTEND_DIR"
echo "[run-frontend] flutter ${FLUTTER_ARGS[*]}"
cd "$FRONTEND_DIR"
exec flutter "${FLUTTER_ARGS[@]}"
