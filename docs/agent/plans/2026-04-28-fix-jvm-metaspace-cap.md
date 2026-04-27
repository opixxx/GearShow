# EXEC_PLAN: fix-jvm-metaspace-cap

- **Type**: fix
- **Status**: completed  <!-- pending | in_progress | completed | error | blocked -->
- **Risk**: Safe
- **Created**: 2026-04-28 01:15
- **Branch**: fix/fix-jvm-metaspace-cap
- **Worktree**: /Users/opix/gearshow-fix-jvm-metaspace-cap
- **Port**: 9000

> Status 전환은 escalation.md 참조. 종료 시 반드시 completed/error/blocked 중 하나로 마무리.

---

## 1. 목표 (Goal)

PR #50 머지 후 CD (run #25005119947) 가 `OutOfMemoryError: Metaspace` 로 실패. t3.small (2GB) 시절에 빡빡하게 잡았던 `MaxMetaspaceSize=128m` 가 t3.medium (4GB) + Redis 활성화로 추가 로딩된 빈/Redisson 클래스를 못 받쳐서 부팅 무한 OOM 루프. JVM 메모리 캡을 t3.medium 기준으로 상향하고 헬스체크 timeout 도 60s→90s 로 완화하여 EC2 prod 백엔드 부팅 정상화.

## 2. 범위 (Scope)

### In
- `docker-compose.prod.yml` 의 `backend.environment.JDK_JAVA_OPTIONS` 메모리 캡 상향:
  - heap: `-Xms384m -Xmx384m` → `-Xms512m -Xmx512m`
  - direct: `-XX:MaxDirectMemorySize=64m` → `=96m`
  - **metaspace: `-XX:MaxMetaspaceSize=128m` → `=256m`** ⭐ (이번 OOM 의 진짜 원인)
- `.github/workflows/cd.yml` 의 헬스체크 루프: `seq 1 12` → `seq 1 18` (60s → 90s)
- `docker-compose.prod.yml` 의 메모리 예산 주석 갱신 (t3.small → t3.medium 기준)

### Out
- JVM 옵션을 application.yml 로 이전 (별도 작업)
- Spring Boot lazy initialization 도입 (별도 작업)
- 헬스체크 endpoint 변경 (`/api/v1/health` → `/actuator/health`) (별도 검토)
- backend 컨테이너 mem_limit 추가 (이번 핫픽스 scope 밖)

## 3. 변경 대상 (Affected)

- **인프라 / 배포**:
  - `docker-compose.prod.yml` — `backend.environment.JDK_JAVA_OPTIONS` + 위쪽 메모리 예산 주석
  - `.github/workflows/cd.yml` — 헬스체크 루프 한 줄
- **docs/**: 본 EXEC_PLAN
- **backend / frontend**: 변경 없음

## 4. 접근 (Approach)

**핵심**: JVM 메모리 캡 한 줄 변경으로 OOM 회피.

### 새 메모리 예산 (t3.medium 4GB)

| 컴포넌트 | 메모리 |
|----------|--------|
| Backend JVM (heap 512 + direct 96 + metaspace 256 + ~80 stack) | ~944MB |
| Kafka | ~370MB |
| Redis | ~192MB |
| Prometheus + Grafana + node-exporter | ~512MB |
| OS | ~80MB |
| **합계** | **~2.1GB / 4GB** (여유 1.9GB) |

### 왜 metaspace 128m → 256m

PR #50 의 `REDIS_ENABLED=true` 로 추가 로딩되는 빈/클래스 (Redisson 30+ 클래스, codecs, plugins, async wrappers + Workflow* + Reconcile* + WorkflowGeneratingConfirmedEventListener 등) 가 기존 128m 안 잡혀서 OOM. 256m 면 여유 충분.

### 왜 헬스체크 60s → 90s

이번 로그:
- 컨테이너 Started 16:03:28
- 첫 부팅 OOM → 자동 재시작 → 두 번째 main start 16:04:13
- 헬스체크 끝 16:04:28 (60s timeout)

OOM 으로 한 번 재시작 일어나면 60s 내 회복 불가. 90s 면 첫 부팅 OOM → 재시작 → 두 번째 부팅 완료 시간 확보 (단 metaspace 캡 상향이 본질적 fix).

**양보 불가 규칙**: t3.medium 4GB 메모리 예산 합계가 3GB 를 넘지 않을 것 (안전 여유 25% 확보).

## 5. 단계 (Steps)

### Step 1: jvm-options-bump

**읽어야 할 파일**:
- `docker-compose.prod.yml` 의 `backend.environment.JDK_JAVA_OPTIONS` + 위쪽 메모리 예산 주석
- 실패 CD 로그 (참조): GitHub Actions run #25005119947 — `OutOfMemoryError: Metaspace`

**작업**:

1. `JDK_JAVA_OPTIONS` 4 값 모두 상향:
   - `-Xms384m -Xmx384m` → `-Xms512m -Xmx512m`
   - `-XX:MaxDirectMemorySize=64m` → `=96m`
   - `-XX:MaxMetaspaceSize=128m` → `=256m`

2. 위쪽 주석의 메모리 예산을 t3.medium 기준으로 갱신, 변경 사유 (PR #51 의 metaspace 상향 이유) 한 줄 추가.

**AC (Bash)**:
```bash
grep -n "MaxMetaspaceSize=256m" /Users/opix/gearshow-fix-jvm-metaspace-cap/docker-compose.prod.yml
grep -n "Xms512m -Xmx512m" /Users/opix/gearshow-fix-jvm-metaspace-cap/docker-compose.prod.yml
! grep -n "Xmx384m" /Users/opix/gearshow-fix-jvm-metaspace-cap/docker-compose.prod.yml
! grep -n "MaxMetaspaceSize=128m" /Users/opix/gearshow-fix-jvm-metaspace-cap/docker-compose.prod.yml
```

**금지사항**:
- backend 서비스에 `mem_limit` 을 추가하지 마라. 이유: Java 프로세스가 cgroup 제한에 걸리면 알 수 없는 형태로 죽음. 측정 후 결정.
- application.yml 의 메모리 관련 설정을 건드리지 마라. 이번 scope 밖.

### Step 2: cd-healthcheck-window-extend

**읽어야 할 파일**:
- `.github/workflows/cd.yml` 의 EC2 배포 step (`for i in $(seq 1 12); do`)

**작업**:

루프 횟수 12 → 18 (60s → 90s) + 주석 갱신.

**AC**:
```bash
grep -n "seq 1 18" /Users/opix/gearshow-fix-jvm-metaspace-cap/.github/workflows/cd.yml
! grep -n "seq 1 12" /Users/opix/gearshow-fix-jvm-metaspace-cap/.github/workflows/cd.yml
```

**금지사항**:
- 루프 안의 `sleep 5` 를 줄이지 마라. EC2 CPU 부담만 늘어남.

## 6. 테스트 계획 (Test Plan)

- **Happy Path**: 다음 push 의 CD 가 backend 컨테이너 OOM 없이 부팅 + 헬스체크 통과
  - 검증: `gh run view <new-run-id> --json conclusion --jq .conclusion` == `success`
  - 검증: `curl http://3.34.25.114:8080/actuator/health` HTTP 200
- **Unhappy Path**: metaspace 또 부족 → 1024m 까지 상향 가능 (메모리 여유 있음)
- **추가 검증**: Java 변경 없음 → 컴파일/단위 테스트 영향 0. compose 문법 검증만.

## 7. 완료 기준 (Done Criteria)

```bash
# 1) compose 문법
echo "ECR_REGISTRY=dummy
IMAGE_TAG=test
GF_ADMIN_USER=x
GF_ADMIN_PASSWORD=y" > /Users/opix/gearshow-fix-jvm-metaspace-cap/.env
docker compose -f /Users/opix/gearshow-fix-jvm-metaspace-cap/docker-compose.prod.yml config --quiet
rm /Users/opix/gearshow-fix-jvm-metaspace-cap/.env

# 2) AC grep 검증
grep -q "MaxMetaspaceSize=256m" /Users/opix/gearshow-fix-jvm-metaspace-cap/docker-compose.prod.yml
grep -q "seq 1 18" /Users/opix/gearshow-fix-jvm-metaspace-cap/.github/workflows/cd.yml
```

추가 정성 기준:
- [ ] EXEC_PLAN Status `completed`
- [ ] PR 본문에 OOM 로그 + 메모리 예산 표 + 변경 라인 명시
- [ ] 머지 후 CD 통과로 검증 (백엔드 8080 응답)
- [ ] 리뷰: 인프라 핫픽스라 가벼운 review (Java 변경 없음)

## 8. 롤백 전략 (Rollback)

`git revert <commit>` 한 번. 단 PR #50 의 OOM 으로 회귀 — 권장 X.

대신 metaspace 또 부족하면:
- compose 의 `MaxMetaspaceSize=256m` 을 `512m` 으로 다시 상향
- 또는 EC2 .env override 로 즉시 핫패치 후 재기동

데이터/스키마/외부 계약 변경 없음.
