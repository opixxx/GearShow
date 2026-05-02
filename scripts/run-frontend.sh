#!/bin/bash
# scripts/run-frontend.sh [--env=dev|prod] [--device=<id>] [--user=<id>] [--no-simulator] [-h|--help]
#
# 프론트엔드(Flutter) 실행 단일 진입점. iOS Simulator 부팅 후 frontend/ 에서
# `flutter run --dart-define=ENV=<env>` 를 실행한다. 매번 시뮬레이터 켜고 디렉토리
# 이동하고 dart-define 옵션을 떠올리는 번거로움을 제거한다.
#
# 옵션:
#   --env=dev|prod    실행 환경 (default: prod). dev 면 카카오 SDK 초기화 스킵.
#   --device=<id>     flutter device id 명시 (default: 디바이스 캐시 → flutter 자동 선택)
#   --user=<id>       DEV_USER_ID 주입 (--env=dev 와 함께만 의미, 그 외엔 경고 후 무시)
#   --no-simulator    iOS Simulator 자동 부팅 스킵 (Android emulator/Chrome 등 사용 시)
#   -h, --help        이 도움말 출력
#
# 환경변수 (셋되어 있으면 dart-define 으로 자동 전달):
#   KAKAO_NATIVE_APP_KEY        카카오 네이티브 앱 키
#   KAKAO_JAVASCRIPT_APP_KEY    카카오 JavaScript 앱 키
#
# 환경변수 자동 로드:
#   실행 시 frontend/.env 가 존재하면, 셸에 미셋된 키만 export 한다.
#   셸 export 가 우선 — KAKAO_NATIVE_APP_KEY=foo bash scripts/run-frontend.sh
#   같은 ad-hoc 주입은 .env 가 덮어쓰지 않는다.
#
# 디바이스 캐시:
#   --device 미지정 시 ~/.gearshow/last-device 가 존재하면 그 값을 fallback 으로 사용.
#   flutter run 이 정상 종료(코드 0, 보통 'q' 입력)했을 때 사용한 디바이스를 같은 파일에
#   저장한다. 잘못된 디바이스로 즉사한 경우(코드 != 0)엔 캐시를 갱신하지 않아 이전 값 보존.
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

# ─── .env fallback 로더 ──────────────────────────────────────────────────────
# 인자로 받은 .env 파일을 읽어, 셸에 아직 셋되지 않은 키만 export 한다.
# 셸 export 가 우선 — 이미 셋된 키는 .env 값으로 덮어쓰지 않는다.
# 파일이 없으면 silent 리턴(기존 동작 유지). 키 값은 절대 출력하지 않는다.
# eval/source 미사용 — 인젝션 차단 목적으로 read 만 사용한다.
load_env_if_missing() {
    local env_file="$1"
    [[ ! -f "$env_file" ]] && return 0

    local line key value
    while IFS= read -r line || [[ -n "$line" ]]; do
        # 주석/공백 라인 스킵
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ "$line" =~ ^[[:space:]]*$ ]] && continue

        # KEY=VALUE 형태만 처리. KEY 는 영문/숫자/언더스코어, 첫 글자는 영문/언더스코어.
        if [[ ! "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
            continue
        fi
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"

        # value 양쪽 따옴표 제거 (.env 일반 관행)
        if [[ "$value" =~ ^\"(.*)\"$ ]]; then
            value="${BASH_REMATCH[1]}"
        elif [[ "$value" =~ ^\'(.*)\'$ ]]; then
            value="${BASH_REMATCH[1]}"
        fi

        # 셸 우선 — 이미 셋되어 있으면 보존
        [[ -n "${!key+x}" ]] && continue

        export "$key=$value"
    done < "$env_file"

    echo "[run-frontend] $env_file 로드 (셸 미셋 키만 fallback)"
}

# ─── 디바이스 캐시 ───────────────────────────────────────────────────────────
# ~/.gearshow/last-device 파일 한 줄(디바이스 id)로 단일 슬롯 캐싱.
# 읽기 실패는 silent (첫 실행). 쓰기 실패는 stderr 경고만 — Flutter 종료 코드 보존.
CACHE_FILE="$HOME/.gearshow/last-device"

# 캐시된 디바이스 id를 stdout으로 출력. 파일 없으면 빈 문자열.
# eval/source 미사용 — 단순 read 만으로 인젝션 차단.
load_cached_device() {
    [[ ! -f "$CACHE_FILE" ]] && return 0
    local cached
    IFS= read -r cached < "$CACHE_FILE" || return 0
    # 양 끝 공백 제거
    cached="${cached#"${cached%%[![:space:]]*}"}"
    cached="${cached%"${cached##*[![:space:]]}"}"
    printf '%s' "$cached"
}

# 디바이스 id를 캐시 파일에 저장. 빈 인자 no-op.
# 쓰기 실패 시 stderr 경고만 출력하고 종료 코드는 호출부에 영향 주지 않는다.
save_cached_device() {
    local device_id="${1:-}"
    [[ -z "$device_id" ]] && return 0

    if ! mkdir -p "$(dirname "$CACHE_FILE")" 2>/dev/null; then
        echo "[run-frontend] 경고: $(dirname "$CACHE_FILE") 생성 실패. 디바이스 캐시 갱신 스킵." >&2
        return 0
    fi
    if ! printf '%s\n' "$device_id" > "$CACHE_FILE" 2>/dev/null; then
        echo "[run-frontend] 경고: $CACHE_FILE 쓰기 실패. 디바이스 캐시 갱신 스킵." >&2
        return 0
    fi
    echo "[run-frontend] 디바이스 저장: $device_id → $CACHE_FILE"
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

# ─── 디바이스 fallback (CLI 미지정 시 캐시 사용) ─────────────────────────────
if [[ -z "$DEVICE" ]]; then
    cached_device="$(load_cached_device)"
    if [[ -n "$cached_device" ]]; then
        DEVICE="$cached_device"
        echo "[run-frontend] 캐시된 디바이스 사용: $DEVICE ($CACHE_FILE)"
    fi
fi

# ─── 경로 계산 (스크립트 위치 기준) ──────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"

if [[ ! -f "$FRONTEND_DIR/pubspec.yaml" ]]; then
    echo "오류: $FRONTEND_DIR/pubspec.yaml 이 없습니다. Flutter 프로젝트 위치를 확인하세요." >&2
    exit 1
fi

# ─── frontend/.env fallback 로드 (셸 미셋 키만) ──────────────────────────────
load_env_if_missing "$FRONTEND_DIR/.env"

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
# exec 미사용 — 종료 코드를 보고 디바이스 캐시 갱신 여부를 결정해야 한다.
# rc != 0 (잘못된 디바이스로 즉사 등) 시 캐시를 덮지 않아 이전 정상값 보존.
echo "[run-frontend] cd $FRONTEND_DIR"
echo "[run-frontend] flutter ${FLUTTER_ARGS[*]}"
cd "$FRONTEND_DIR"

rc=0
flutter "${FLUTTER_ARGS[@]}" || rc=$?

if [[ $rc -eq 0 && -n "$DEVICE" ]]; then
    save_cached_device "$DEVICE"
fi

exit $rc
