# ADR-027: 3D Worker 프로세스 분리 — 단일 아티팩트 + 프로파일 역할 분기

- **상태**: Accepted
- **결정일**: 2026-05-19
- **관련 PR**: refactor/3d-worker-process-split-p1 (PR1 — 스캐폴딩) · 후속 PR2 (배포 분리)
- **결정 주체**: GearShow Backend
- **관련 문서**: [3D 생성 파이프라인 설계 §2](../../research/2026-04-23-3d-generation-pipeline-design.md)
- **관련 ADR**: [ADR-013](./ADR-013-redis-as-mandatory-infra.md), [ADR-025](./ADR-025-3d-generation-admission-queue.md), [ADR-026](./ADR-026-3d-preparing-retryable-compensation.md)

---

## 1. 배경 (Context)

GearShow 백엔드는 단일 Spring Boot 모놀리스(`backend`, Gradle 단일 모듈, 단일 Dockerfile)다. HTTP API · Kafka Consumer(3D 생성) · 스케줄러 5종이 한 프로세스에서 실행되고, 운영은 EC2 1대(t3.medium, 4GB/2vCPU — PR #51 에서 t3.small→t3.medium 업그레이드)에 `backend` 컨테이너 1개로 배포된다.

3D 생성 파이프라인은 무거운 외부 I/O(Tripo 업로드·`POST /task`·폴링·재시도)를 수반한다. 이것이 API 응답 경로 및 공용 인프라 스케줄러와 한 프로세스/한 스케줄러 스레드를 공유하는 데서 두 가지 문제가 누적됐다:

1. **F1 — 단일 스케줄러 스레드 기아 (PR #96 리뷰, 2026-04-28 사고 클래스)**: `@EnableScheduling` 은 커스텀 `TaskScheduler`/`spring.task.scheduling.pool` 설정이 없어 기본 `ThreadPoolTaskScheduler`(core=1) 를 쓴다. `OutboxRelayScheduler`(1s) · `ReconcileScheduler`(60s) · `AdmissionQueueDrainScheduler`(5s) 가 단일 스레드에서 **직렬화**된다. 한 작업의 블로킹(예: Kafka-down 시 드레이너 `KafkaTemplate.send` 가 `max.block.ms` 까지 점유)이 나머지를 굶긴다 — 특히 입장 큐 유실의 유일 보상 경로인 Reconcile 이 기아하면 correctness 위험.
2. **실행/자원 격리 부재**: 3D 부하만 독립적으로 스케일하거나 API 가용성과 분리할 수단이 없다.

"worker 분리"의 본질은 코드 모듈 분리가 아니라 **실행/자원 격리 + 스케줄러 소유권 분리**다. 분리 경계는 곧 "어느 프로세스가 어떤 스케줄러/컨슈머를 소유하는가"이며, 입장 큐(ADR-025)가 흐름 제어 지점이 된 뒤에야 분리 순서가 성립한다.

## 2. 결정 (Decision)

**단일 JAR 아티팩트를 Spring 프로파일로 `api` / `worker` 역할 분기 실행한다. 같은 t3.medium 에 컨테이너 2개로 배포한다.** 2 페이즈로 나눠 PR1(본 스캐폴딩) 은 코드·설정·문서만, PR2 가 배포 토폴로지(docker-compose.prod·CD)를 변경한다.

### 2.1 판별자 = 세분화 조건부 플래그 + 프로파일 번들 (`spring.kafka.enabled` 아님)

api·worker 둘 다 Kafka 클라이언트가 필요하다(api = Outbox→Kafka producer, worker = Consumer + 드레이너 producer). 따라서 기존 `spring.kafka.enabled` 단일 플래그로는 두 역할을 가를 수 없다. 신규 토글을 도입한다:

| 빈 | 게이트 | 형식 |
|---|---|---|
| `OutboxRelayScheduler` | `spring.kafka.enabled` **AND** `app.outbox.relay-enabled` | `@ConditionalOnExpression`(`AdmissionQueueDrainScheduler:52` 기존 선례 동일 패턴) |
| `ModelGenerationWorker` | `spring.kafka.enabled` **AND** `app.model-generation.worker-enabled` | `@ConditionalOnExpression` |
| `OutboxCleanupScheduler` | `app.outbox.cleanup-enabled` | `@ConditionalOnProperty(matchIfMissing=true)` |
| `ProcessedMessageCleanupScheduler` | `app.idempotency.cleanup-enabled` | `@ConditionalOnProperty(matchIfMissing=true)` |

기존 플래그 재사용(코드 무변경, 프로파일 YAML 로만 토글): `app.model-generation.admission.enabled`(드레이너, `:true` 기본) · `app.workflow-polling.scheduler-enabled`(Poller, matchIfMissing) · `app.reconcile.enabled`(Reconcile, **opt-in** — `application-prod.yml` 명시 `true`).

### 2.2 역할 ↔ 컴포넌트 소유 매트릭스

| 컴포넌트 | api | worker | 근거 |
|---|:--:|:--:|---|
| HTTP 컨트롤러 | 트래픽 | 떠 있으나 무트래픽 | 라우팅은 PR2 |
| OutboxRelayScheduler | ✅ | ❌ | 공용 플랫폼 인프라 = always-on api 단독 소유(2-프로세스 중복 발행 차단) |
| Outbox/Idempotency Cleanup | ✅ | ❌ | 공용 정리 배치 = api 단독(중복 정리 차단) |
| ModelGenerationWorker (Kafka Consumer) | ❌ | ✅ | 3D 무거운 작업 격리 |
| AdmissionQueueDrainScheduler | ❌ | ✅ | ADR-025 드레이너 |
| ReconcileScheduler | ❌ | ✅ | 3D stuck 복구 안전망 |
| WorkflowPollingScheduler | ❌ | ✅ | Tripo 폴링 루프 |

### 2.3 F1 해소 — 프로세스별 스케줄러 풀

`application.yml` 에 `spring.task.scheduling.pool.size: 3` 을 **전역** 추가한다. (a) 현 모놀리스의 F1 을 즉시 해소(직렬화→동시 실행. 세 스케줄러는 독립 관심사이며 각자 자체 Redis 락[reconcile lock·drain-lock] 보유라 동시성 안전). (b) 분리 후 각 JVM 이 자기 스케줄러 풀을 갖고, worker 의 Reconcile+AdmissionDrain 가 서로 굶지 않는다. `WorkflowPollingScheduler` 는 `tripoPollingExecutor`(@Async) 별도 풀이라 무관. **이는 PR1 의 유일한 의도적 운영 동작 변경**이다.

### 2.4 운영 무회귀 (양보 불가)

신규 토글은 전부 기본 활성(`matchIfMissing=true` 또는 expression `:true`). 운영은 현행 `SPRING_PROFILES_ACTIVE=prod`(api/worker 없음) → 신규 토글 미설정 → 전 컴포넌트 활성 = **현 모놀리스와 동일**. `application-{api,worker}.yml` 은 PR2 가 `SPRING_PROFILES_ACTIVE` 에 추가하기 전까지 inert. `application-prod.yml` 미수정(무회귀 reviewability 보존). 토글 기본값은 `application.yml` 에 명시(프로파일 YAML 키 오타가 silent-ignore 되어 중복 relay 가 조용히 켜지는 사고를 코드 리뷰에서 차단).

### 2.5 역할별 JVM 메모리 예산 (PR2 적용 — 양보 불가: 수치 박제)

| 프로세스 | 제안 JVM 옵션 | 추정 |
|---|---|---|
| (현행 단일) | `-Xms512m -Xmx512m -XX:MaxDirectMemorySize=96m -XX:MaxMetaspaceSize=256m` | ~944 MB |
| api | `-Xms384m -Xmx384m -XX:MaxDirectMemorySize=80m -XX:MaxMetaspaceSize=224m` | ~760 MB |
| worker | `-Xms320m -Xmx320m -XX:MaxDirectMemorySize=80m -XX:MaxMetaspaceSize=224m` | ~700 MB |

합계 ≈ 1.46 GB(현 944 MB 대비 +~520 MB). 신규 총합 ≈ 2.1 + 0.52 ≈ **2.6 GB / 4 GB → 여유 ~1.4 GB**. metaspace 중복(~224m×2, 같은 JAR 라 거의 같은 클래스 로딩)이 불가피 최대 비용. **landmine**: PR #51(2026-04-28) 에서 metaspace 한도 초과로 부팅 OOM 무한루프(CD run 사고) 전례 → PR2 배포 시 위 예산 적용 + **부팅 검증 게이트 필수**. 수치는 *제안/검증 대상*이며 PR2 배포 후 실측으로 확정한다.

## 3. 고려한 대안 (Alternatives)

### A. 별도 Gradle 모듈(worker-app) 추출 — 기각(현 시점)
멀티모듈로 worker 부트앱을 분리(domain/persistence 공유). 강한 격리·독립 빌드 가능하나 Gradle/CI 복잡도와 변경 분량이 크다. 출시 전 단계에 비용 대비 가치 낮음. 단일 아티팩트로 격리 목표(스케줄러 소유권·실행 분리)는 충족된다. 출시 후 필요 시 재검토.

### B. 마이크로서비스 추출(별도 repo/DB) — 기각
공유 DB(`model_generation_workflow`/`outbox`)·Redis·Kafka 가 설계 전제(ADR-010/011/012/013/025)다. 별도 DB 분리는 멱등성·Outbox·입장 큐 모델을 전면 재설계해야 하므로 출시 전 과설계.

### C. 별도 EC2 인스턴스로 worker 배포 — 기각(현 시점)
완전 자원 격리·독립 스케일 가능하나 인프라 비용·CD 복잡도 증가. 같은 t3.medium 2컨테이너로 F1 의 핵심(스케줄러 스레드 기아)은 해소되고(프로세스 분리 → 각자 스케줄러 풀), 메모리 예산도 4GB 안에 수용 가능(§2.5). 토폴로지 1→2 전환은 docker-compose 변경만으로 가역적이라 출시 후 트래픽 증가 시점에 미룬다.

### D. `@Profile("worker")` 어노테이션 직접 부착 — 기각
빈마다 `@Profile` 을 다는 안. 코드베이스는 이미 `@ConditionalOnProperty`/`@ConditionalOnExpression` 토글 메커니즘을 쓰고(`WorkflowPollingScheduler`·`ReconcileBeansConfig`·`AdmissionQueueDrainScheduler`), 프로파일 YAML 이 그 프로퍼티를 set 하는 방식이 기존 패턴과 일관·저침습. `@Profile` 이중화는 드리프트 위험.

## 4. 결과 (Consequences)

### 긍정
- F1(2026-04-28 사고 클래스) 이 현 모놀리스에서 **즉시** 해소(`pool.size=3`)되고, 분리 후에는 프로세스 격리로 구조적으로 차단.
- 3D 무거운 작업이 API 응답·공용 인프라 스케줄러와 프로세스 분리 → 한쪽 장애·부하의 격리.
- 스캐폴딩이 inert(운영 무회귀)라 PR1 은 안전하게 머지 가능. 분리 활성화(PR2)와 시점 분리 → 리스크 분산·롤백 명확.
- 단일 아티팩트라 빌드/CI 변경 0, 토폴로지 전환 가역적.

### 잔여 / 수용
- 같은 호스트 2컨테이너는 **CPU 는 여전히 공유**(2 vCPU) — OS 레벨 완전 격리 아님. F1 의 스레드 기아는 해소되나 CPU 경합은 출시 후 트래픽 시점에 별도 인스턴스(대안 C)로 재검토. Tripo 경로는 I/O-bound 라 출시 전 수용 가능.
- worker 프로세스도 HTTP 컨트롤러 빈을 띄운다(트래픽 미라우팅이라 무해). 컨트롤러 레벨 비활성화는 출시 후(불필요한 선최적화 회피).
- metaspace 중복(~224m×2)은 단일 JAR 의 본질적 비용. §2.5 예산은 실측 전 제안값 — PR2 배포 검증 게이트로 확정.
- `application-prod.yml` line 167-170 의 "t3.small 2GB" 주석은 stale(권위값=PR #51 주석의 t3.medium 4GB). PR2(배포 파일 수정 시) 정정.
- F2~F6 / ADR-025 Deferred D1~D6(메모리 백로그)는 본 ADR 범위 밖 — 보류 유지.

### Deferred (PR2 — 배포 분리)
docker-compose.prod.yml 의 `backend` 1서비스 → `api`/`worker` 2서비스 분리(역할별 `SPRING_PROFILES_ACTIVE`·JVM 옵션·포트) · CD 워크플로 2컨테이너 배포 · 배포 후 메모리 실측 검증 게이트 · stale 주석 정정.

### 검증
`RoleProfileSplitTest`(ApplicationContextRunner + ConfigDataApplicationContextInitializer): (1) prod 베이스라인 무회귀 가드(신규 토글 전부 활성) · (2) `spring.task.scheduling.pool.size=3` · (3) api/worker 프로파일 번들 정확성 · (4) 신규 조건부 어노테이션 빈 등록/비등록 + `@ConditionalOnProperty→@ConditionalOnExpression` 변환 동등성(kafka-off 무회귀).
