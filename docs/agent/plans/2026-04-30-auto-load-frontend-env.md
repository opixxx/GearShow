# EXEC_PLAN: auto-load-frontend-env

- **Type**: chore
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Safe
- **Created**: 2026-04-30 15:44
- **Branch**: chore/auto-load-frontend-env
- **Worktree**: /Users/opix/GearShow/../gearshow-auto-load-frontend-env
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

`scripts/run-frontend.sh` 가 `frontend/.env` 를 자동으로 로드하여, 매번 셸에서 `set -a; source frontend/.env; set +a` 하지 않아도 카카오 키가 dart-define 으로 전달되도록 한다. 시크릿 운영 안전성을 위해 **셸에 이미 export 된 값이 우선**이고 .env 는 fallback 으로만 동작한다.

## 2. 범위 (Scope)

### In
- `scripts/run-frontend.sh` 수정 — `frontend/.env` 가 존재하면 셸 미셋 키만 export 하는 안전 로드 로직 추가
- 안내 출력 한 줄 (`[run-frontend] frontend/.env 로드 (셸 미셋 키만 fallback)`)
- `--help` 본문에 .env 자동 로드 동작 명시

### Out
- `backend/.env` 자동 로드 — 이 스크립트는 frontend 전용
- `KAKAO_NATIVE_APP_KEY` / `KAKAO_JAVASCRIPT_APP_KEY` 외 다른 .env 키를 dart-define 으로 자동 전달 — 사용자 요청 "기존 동작 그대로"
- `.env` 부재 시 에러 — 기존 동작(silent skip) 유지. dev 환경/CI 에서 .env 없이도 띄울 수 있어야 함
- `direnv` / `dotenv-cli` 등 외부 도구 의존 도입 — 셸 내장만 사용

## 3. 변경 대상 (Affected)

- **domain/**: 없음
- **application/**: 없음
- **adapter/**: 없음
- **scripts/**: `scripts/run-frontend.sh` 수정
- **docs/**: 없음 (사용법은 `--help` 갱신으로 흡수)

## 4. 접근 (Approach)

### sh 동작 흐름 (변경 후)
1. 인자 파싱 (기존 그대로)
2. **`frontend/.env` 자동 로드** *(신규)* — 파일이 존재하면 셸 미셋 키만 export
3. iOS Simulator 부팅 (기존 그대로)
4. `cd frontend` 후 `flutter run --dart-define=ENV=<env>` (기존 그대로)

### 핵심 인터페이스 (셸 함수)

```bash
# load_env_if_missing <env_file>
#   env_file 의 KEY=VAL 라인을 읽어, 셸에 아직 셋되지 않은 키만 export 한다.
#   셸 export 가 우선 — 이미 셋된 키는 .env 값으로 덮어쓰지 않는다.
#   주석(#)·빈 라인 무시. 잘못된 형식(KEY 미존재)은 무시.
load_env_if_missing() { ... }
```

### 양보 불가 규칙

- **시크릿 하드코딩 금지** — 키 값 자체를 sh 안에 두지 않음 (기존 정책 유지)
- **셸 export 우선** — `KAKAO_NATIVE_APP_KEY=other bash scripts/run-frontend.sh` 같은 ad-hoc 주입을 .env 가 덮지 않음. CI 에서 환경변수로만 주입할 때 stale .env 가 영향 주지 않음
- **`eval` 금지** — 인자/.env 파싱 모두 셸 builtin 으로 (인젝션 방지)
- **`set -euo pipefail` 유지** — 단, .env 부재는 silent skip 하므로 `[ -f ... ]` 가드
- **`flutter run` stdout/stderr redirect 금지** (기존 정책 유지)
- **로드 안내 출력** — `[run-frontend] frontend/.env 로드 (셸 미셋 키만 fallback)` 한 줄. 키 값은 절대 출력하지 않음
- **셸 위치 기준 상대경로** — `dirname "$0"` 으로 frontend/.env 경로 계산 (기존 정책 유지)

### .env 파싱 정책

- `KEY=value` 형태 — `=` 좌측이 영문/숫자/언더스코어로만 구성된 라인만 처리
- value 의 양쪽 따옴표(`"..."` / `'...'`)는 제거 — 일반적인 .env 관행
- value 안에 `=` 가 있어도 첫 `=` 까지만 KEY 로 본다
- `#` 으로 시작하는 라인, 빈 라인, KEY 형식 위반 라인은 조용히 무시
- 셸 builtin 만 사용 (read, IFS, parameter expansion). awk/sed 도 허용하되 `eval` 금지

## 5. 단계 (Steps)

### Step 1: write-load-env-function

**읽어야 할 파일**:
- `/Users/opix/gearshow-auto-load-frontend-env/scripts/run-frontend.sh` — 기존 인자 파싱/usage 패턴/주석 스타일 참조
- `/Users/opix/gearshow-auto-load-frontend-env/frontend/.env` — **키 이름만** 확인 (값 노출 금지). `awk -F= '/^[A-Z_]+=/ {print $1}' frontend/.env` 로 충분
- `/Users/opix/GearShow/docs/agent/plans/2026-04-30-add-run-frontend-script.md` — 원본 스크립트 양보 불가 규칙 (시크릿 하드코딩 금지, eval 금지, redirect 금지)

**작업**:

`scripts/run-frontend.sh` 에 다음을 추가/수정한다:

1. 인자 파싱 직후, Simulator 부팅 전에 `load_env_if_missing` 함수를 정의·호출
2. 함수 인터페이스:
   ```
   load_env_if_missing <env_file>
   - 파일이 존재하지 않으면 silent return 0
   - 존재하면 한 줄씩 읽어 KEY=VAL 형태 파싱
   - KEY 가 셸에 이미 셋되어 있으면(`[ -n "${!KEY+x}" ]`) 스킵
   - 미셋이면 export
   - 파싱 후 안내 한 줄 출력 (한국어, 키 값은 출력 X)
   ```
3. 호출 시점: 스크립트 위치 기준으로 `frontend/.env` 경로 계산하여 호출
4. `usage()` 본문(헤더 주석 블록)에 .env 자동 로드 동작 한 단락 추가:
   ```
   환경변수 자동 로드:
     실행 시 frontend/.env 가 존재하면, 셸에 미셋된 키만 export 한다.
     셸 export 가 우선 — KAKAO_NATIVE_APP_KEY=foo bash scripts/run-frontend.sh
     같은 ad-hoc 주입은 .env 가 덮어쓰지 않는다.
   ```

**AC (Bash)**:
```bash
WT=/Users/opix/gearshow-auto-load-frontend-env

# 1) 문법 통과
bash -n "$WT/scripts/run-frontend.sh"

# 2) 실행 권한 유지
test -x "$WT/scripts/run-frontend.sh"

# 3) --help 가 .env 자동 로드 동작을 노출
"$WT/scripts/run-frontend.sh" --help | grep -q "frontend/.env"
"$WT/scripts/run-frontend.sh" --help | grep -q "셸 export 가 우선"

# 4) shellcheck 통과 (설치돼 있으면)
command -v shellcheck >/dev/null && shellcheck "$WT/scripts/run-frontend.sh" || true

# 5) 함수 단독 동작 검증 — 임시 .env 로 dry-run
TMPENV=$(mktemp)
cat >"$TMPENV" <<'EOF'
KAKAO_NATIVE_APP_KEY=value_from_dotenv
KAKAO_JAVASCRIPT_APP_KEY=value_from_dotenv
EOF

# 5-a) 셸 미셋 상태 → .env 값으로 export 되어야 함
unset KAKAO_NATIVE_APP_KEY KAKAO_JAVASCRIPT_APP_KEY
RESULT=$(bash -c "
  source <(awk '/^load_env_if_missing\(\)/,/^}/' '$WT/scripts/run-frontend.sh')
  load_env_if_missing '$TMPENV'
  echo \"NATIVE=\${KAKAO_NATIVE_APP_KEY:-UNSET}\"
  echo \"JS=\${KAKAO_JAVASCRIPT_APP_KEY:-UNSET}\"
")
echo "$RESULT" | grep -q "NATIVE=value_from_dotenv"
echo "$RESULT" | grep -q "JS=value_from_dotenv"

# 5-b) 셸에 이미 셋된 값은 보존 (셸 우선)
RESULT=$(KAKAO_NATIVE_APP_KEY=shell_wins bash -c "
  source <(awk '/^load_env_if_missing\(\)/,/^}/' '$WT/scripts/run-frontend.sh')
  load_env_if_missing '$TMPENV'
  echo \"NATIVE=\$KAKAO_NATIVE_APP_KEY\"
")
echo "$RESULT" | grep -q "NATIVE=shell_wins"

# 5-c) 파일 부재 시 silent skip (exit 0)
bash -c "
  source <(awk '/^load_env_if_missing\(\)/,/^}/' '$WT/scripts/run-frontend.sh')
  load_env_if_missing '/nonexistent/.env'
"

rm -f "$TMPENV"
```

**금지사항**:
- `eval` 또는 `source <(echo ...)` 같은 동적 코드 실행으로 .env 파싱하지 마라 — 인젝션 위험. `read` + parameter expansion 으로 충분.
- 키 값을 stdout/stderr 에 출력하지 마라 — 시크릿 노출. 안내는 "로드했다"만, 키 이름조차 출력하지 마라.
- `set -e` 우회 목적으로 `|| true` 를 광범위하게 쓰지 마라 — 의도한 silent skip 지점(`[ ! -f ... ] && return 0`)에만 한정.
- `frontend/.env` 외 다른 .env 파일을 자동 로드하지 마라 — scope 외.
- 기존 인자 파싱·dart-define 조립·flutter run 호출부를 건드리지 마라 — 회귀 위험.
- `flutter run` 의 stdout/stderr 를 redirect 하지 마라 — hot reload 키 입력 안내가 안 보임.

## 6. 테스트 계획 (Test Plan)

- **Happy Path (자동, AC 5-a)**: 임시 .env 로드 후 KAKAO_NATIVE_APP_KEY/JS_KEY 가 export 되는지 검증
- **Unhappy Path (자동, AC 5-c)**: .env 가 없을 때 함수가 exit 0 으로 silent skip 하는지 검증
- **셸 우선 정책 (자동, AC 5-b)**: 셸에 이미 export 된 값이 .env 값으로 덮이지 않는지 검증
- **--help 회귀 (자동, AC 3)**: .env 자동 로드 동작이 `--help` 에 노출되는지 검증
- **수동 검증**: PR 머지 후 `frontend/.env` 가 있는 환경에서 `bash scripts/run-frontend.sh --device=<id>` 실행 → 카카오 미설정 경고가 사라지고 카카오 SDK 가 정상 초기화되는지 확인

## 7. 완료 기준 (Done Criteria — Bash 실행 가능)

```bash
WT=/Users/opix/gearshow-auto-load-frontend-env

# 셸 검증 (이 작업은 백엔드 변경 무관 — gradle build 불필요)
bash -n "$WT/scripts/run-frontend.sh"
test -x "$WT/scripts/run-frontend.sh"
"$WT/scripts/run-frontend.sh" --help | grep -q "frontend/.env"
command -v shellcheck >/dev/null && shellcheck "$WT/scripts/run-frontend.sh" || true
```

추가 정성 기준:
- [x] AC 5-a/5-b/5-c 전부 통과 (셸 우선·로드·silent skip)
- [x] code-reviewer Critical 지적 0건 (셸 스크립트 — 시크릿/인젝션/eval 사용 검토 셀프 리뷰로 갈음)
- [x] architecture-reviewer / database-optimizer **스킵** — 백엔드 헥사고날·DB 무관
- [x] EXEC_PLAN 의 Status 필드를 `completed` 로 갱신
- [x] `--help` 본문에 .env 자동 로드 단락 추가됨

## 8. 롤백 전략 (Rollback)

해당 없음 — 셸 스크립트 1개 부분 수정. DB/이벤트/공개 API 무관. 회귀 시 `git revert <commit>` 1줄. 실행 환경에 영향 없음(.env 자동 로드 실패 시에도 사용자가 기존 방식 `set -a; source frontend/.env; set +a` 으로 우회 가능).
