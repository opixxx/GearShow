# ADR-010: 3D 파이프라인 — 도메인/프로세스 테이블 분리

- **상태**: Accepted
- **결정일**: 2026-04-23
- **관련 PR**: docs/adr-010-011-012-3d-pipeline
- **결정 주체**: GearShow Backend
- **관련 문서**: [3D 생성 파이프라인 설계 v1.1](../../research/2026-04-23-3d-generation-pipeline-design.md)
- **관련 ADR**: [ADR-011](./ADR-011-3d-pipeline-multilayer-idempotency.md), [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md)

---

## 1. 배경 (Context)

초기 스키마는 `sc_3d_model` 하나의 테이블에 **모델 산출물의 도메인 상태** (`model_url`, `format`, `file_size`) 와 **파이프라인 프로세스 상태** (`retry_count`, `failure_reason`, `last_polled_at`, `tripo_task_id`, `status`) 를 섞어서 보관하는 구조였다.

이 구조가 Phase 1 설계 논의 중에 드러낸 문제:

1. **재시도 이력 소실** — 재시도는 기존 행을 덮어쓰기 때문에 이전 시도의 `failure_reason` · 경과 시간이 사라진다. 장애 디버깅 시 "몇 번째 재시도에서 어떤 실패" 를 추적 불가.
2. **stuck 임계 분기 불가** — `status=GENERATING` 내부에도 "Tripo 처리 중" 과 "S3 미러링 중" 서브 단계가 있는데, 하나의 테이블에 단일 `updated_at` 만 있어서 단계별로 다른 임계값을 적용하기 어렵다.
3. **의미 혼재** — "이 쇼케이스에 3D 모델이 있는가?" 라는 도메인 질문과 "이 워크플로우의 현재 재시도 횟수는?" 이라는 프로세스 질문이 같은 테이블을 거친다. 읽기 쿼리 · 인덱스 설계가 양쪽의 엇갈리는 요구에 끌려다닌다.
4. **도메인 계층 오염** — `sc_3d_model` 은 본래 "완성된 3D 모델" 이라는 도메인 개념인데, `retry_count` · `failure_source` 같은 프로세스 필드가 섞이면 도메인 모델 메서드가 (아직 완성 안 된 모델의) null 필드를 방어해야 한다.

## 2. 결정 (Decision)

3D 모델 생성 파이프라인의 데이터 모델을 **두 테이블로 분리** 한다.

```
sc_3d_model (도메인 — 완성품 스냅샷)
  id, showcase_id (UNIQUE),
  model_url, format, file_size,
  created_at, updated_at

model_generation_workflow (프로세스 — 생명주기)
  id, showcase_id (FK),
  idempotency_key (UNIQUE),
  attempt_no,
  current_step ENUM(REQUESTED, PREPARING, GENERATING, COMPLETED, FAILED),
  tripo_task_id, tripo_trace_id, tripo_succeeded_at,
  retry_count, failure_code, failure_message, failure_source,
  last_polled_at, heartbeat_at,
  started_at, finished_at, created_at, updated_at,
  INDEX (current_step, heartbeat_at),
  INDEX (current_step, tripo_succeeded_at),
  INDEX (showcase_id, attempt_no)
```

**재시도 = 새로운 workflow 행 INSERT** (`attempt_no` +1). 이전 attempt 의 `failure_*` 는 그대로 보존된다.

`sc_3d_model` 의 `model_url` 은 `model_generation_workflow.current_step = COMPLETED` 로 전이되는 순간 한 번만 UPSERT 된다. 완성품 교체 외엔 건드리지 않음.

### 양보 불가 규칙

- `sc_3d_model` 에는 **프로세스 상태 컬럼 0개** (status · retry_count · failure_* · last_polled_at · heartbeat_at 등 모두 `model_generation_workflow` 쪽에만).
- 재시도는 **항상 새 workflow 행** (UPDATE 로 기존 행 재사용 금지). 이유: 이력 보존.
- `sc_3d_model.showcase_id` UNIQUE — 쇼케이스당 3D 모델 하나.
- `model_generation_workflow` 의 상태 전이는 모두 **조건부 UPDATE** (ADR-012 참조).
- `model_source_image` 는 **현재 attempt 의 4장만** 보유한다 (재시도 시 hard delete + insert). 이력은 `model_generation_workflow.attempt_no` 가 보존하므로 source image 자체는 재시도마다 갈아끼운다. 이 규칙이 깨지면 Tripo multiview 가 4장 초과 입력을 거부 (HTTP 400 / tripoCode 1004) 한다 — 2026-04-28 운영 사고로 명문화.

## 3. 고려한 대안 (Alternatives)

### A. 단일 테이블 유지 + nullable 컬럼으로 혼재

- 장점: 스키마 변경 없음. JOIN 불필요.
- 단점: 재시도 이력 소실, 단계별 stuck 임계 적용 불가, 도메인/프로세스 의미 혼재.
- 판단: **기각**. 1번 문제(이력 소실) 하나만으로도 장애 디버깅에 치명적.

### B. 단일 테이블 + `sc_3d_model_retry_history` 별도 로그 테이블

- 장점: 현재 상태 조회는 단일 테이블에서 끝남. 이력은 별도 로그로.
- 단점: "현재 진행 중인 workflow 의 실시간 상태" 와 "완성된 도메인 스냅샷" 의 의미 혼재는 여전. 로그 테이블 INSERT 는 기존 UPDATE 에 부가적으로 붙어 트랜잭션 경계 복잡.
- 판단: **기각**. B 는 재시도 이력 문제만 완화할 뿐 의미 혼재 · stuck 임계 분기 문제는 해결 못 함.

### C. Event Sourcing (`model_generation_events` append-only)

- 장점: 모든 상태 전이가 감사 로그로 남음. 프로젝션 추가 · 새 뷰 생성이 스키마 마이그레이션 없이 가능. 이력 · 현재 상태 · 재생 모두 자연스럽게 해결.
- 단점: 현재 상태 조회가 항상 집계 → 프로젝션 테이블 캐싱 필수. 초기 구현 복잡도 ↑. "이벤트 타입당 schema drift" 운영 비용.
- 판단: **Phase 2 로 보류**. Phase 1 MVP 범위엔 과함. 운영 데이터 지표 수요가 생긴 후 `model_generation_workflow` 를 이벤트 프로젝션으로 재정의하는 경로는 열어둠.

### D. 단일 `workflow` 테이블로 도메인까지 흡수 (`model_url` 을 workflow 에 저장)

- 장점: 테이블 1개만으로 단순.
- 단점: 재시도 시 새 행 → `model_url` 은 어느 attempt 것을 "최종" 으로 볼지 모호. 쇼케이스에서 3D 모델 조회 시 `MAX(attempt_no) WHERE status=COMPLETED` 같은 복잡 쿼리 필요. 도메인 계층이 프로세스 테이블을 역참조해야 함.
- 판단: **기각**. 도메인의 핵심 질문("이 쇼케이스에 3D 모델이 있는가?") 이 프로세스 조인에 의존하게 되는 게 아키텍처상 반대 방향.

## 4. 결과 (Consequences)

### 긍정

- **재시도 이력 보존** — attempt 별 `failure_code` · 경과 시간 · Tripo trace ID 가 모두 남아 장애 디버깅 가능.
- **단계별 stuck 임계** — `current_step` + `tripo_succeeded_at` + `heartbeat_at` 조합으로 REQUESTED/PREPARING/GENERATING(Tripo 처리)/GENERATING(S3 미러링)/DOWNLOADING 각 단계에 다른 임계값 설정 (설계 §8.4).
- **도메인 계층 깔끔** — `Showcase3dModel` 도메인 모델은 "이 쇼케이스의 3D 모델" 이라는 본래 의미만 담고 null 방어 불필요.
- **인덱스 설계 분리** — 도메인 조회 인덱스(`showcase_id`) 와 프로세스 조회 인덱스(`current_step, heartbeat_at`) 가 충돌 없음.
- **Phase 2 Event Sourcing 으로 확장 경로 확보** — `model_generation_workflow` 는 이벤트 프로젝션의 한 형태로 자연스럽게 재정의 가능.

### 부정

- **신규 테이블 1개 추가** — 마이그레이션 · ORM 매핑 · 리포지토리 · 인덱스 관리 항목 증가.
- **JOIN 비용** — 쇼케이스 상세 조회 시 `showcase + sc_3d_model + model_generation_workflow (MAX attempt)` 조인 필요. 단, `sc_3d_model` 에 완성품이 있는 경우는 workflow 조인 불필요 (도메인 조회만).
- **트랜잭션 경계 주의** — 완료 전이는 `workflow UPDATE + sc_3d_model UPSERT` 2-쓰기 원자 처리 필요 (단일 TX 내).

### 검증

- P1-A 스키마 마이그레이션 PR 에서 DDL 검증.
- ArchUnit: domain 계층에서 `model_generation_workflow` 테이블/엔티티 직접 참조 0건.
- 통합 테스트: 재시도 시나리오에서 이전 attempt 의 `failure_code` 가 그대로 보존되는지 확인 (P1-I).

## 5. 참조

- 설계 문서: [`docs/research/2026-04-23-3d-generation-pipeline-design.md`](../../research/2026-04-23-3d-generation-pipeline-design.md) §3.2~3.3 · §13
- 관련 ADR: ADR-011 (멱등성), ADR-012 (동시성)
- 참조 패턴: Transactional Outbox (ADR-006 유사 구조의 워크플로우 버전)
