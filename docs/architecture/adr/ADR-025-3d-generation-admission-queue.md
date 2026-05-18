# ADR-025: 3D 생성 동시 작업 입장 큐 — 재발행(republish) 방식

- **상태**: Accepted
- **결정일**: 2026-05-18
- **관련 PR**: refactor/3d-admission-queue-republish
- **결정 주체**: opix
- **폐기 이력**: 본 ADR 의 1차 시도(PR #95, inline-drain) 는 **머지하지 않고 close**. 사유는 §3-A.
- **관련 문서**: [3D 생성 파이프라인 설계 v1.2](../../research/2026-04-23-3d-generation-pipeline-design.md) §3.4 · §6 · §7 · §8.4
- **관련 ADR**: [ADR-010](./ADR-010-3d-pipeline-table-split.md), [ADR-011](./ADR-011-3d-pipeline-multilayer-idempotency.md), [ADR-012](./ADR-012-3d-pipeline-conditional-update-concurrency.md), [ADR-013](./ADR-013-redis-as-mandatory-infra.md)

---

## 1. 배경 (Context)

3D 생성 파이프라인은 사용자 요청을 Outbox → `showcase.model-generation.request` Kafka 토픽으로 흘려보내고, `ModelGenerationWorker` 가 소비해 `PrepareWorkflowService` 가 Tripo `POST /task`(과금 지점) 까지 진행한다. 트래픽 피크 시 동시에 생성 중(`current_step IN (PREPARING,GENERATING)`)인 작업 수를 cap 이하로 제어해 Tripo 크레딧 페이싱·부하를 관리할 장치가 필요하다.

## 2. 결정 (Decision)

동시 생성 작업 수를 soft cap(기본 10) 이하로 제어하는 **입장 게이트 + 재발행 드레이너** 를 도입한다.

- **게이트**: `PrepareWorkflowService.prepare()` 내부, `validateSourceImages` 통과 후 ∧ 분산 락 전 ∧ 과금 `POST /task` 전. 활성 수(DB `countActive()` = `current_step IN (PREPARING,GENERATING)`, DB 가 SoT — 누수-free) 가 cap 이상이면 `tripo:queue` ZSET 에 `parkIfAbsent(now)` 후 REQUESTED 유지한 채 정상 반환. Worker 가 `markProcessed` 하여 Kafka 재전송이 끊긴다.
- **드레이너**: `AdmissionQueueDrainScheduler` 가 슬롯 여유 시 ZSET 에서 FIFO pop 후 **Kafka 로 `bypassAdmission=true` 메시지를 async 재발행만** 한다. Tripo I/O 를 절대 하지 않는다. 실행·재시도·DLT 는 기존 Worker(`@RetryableTopic`) 가 담당한다.
- **라우팅**: Worker 는 `bypassAdmission=true` 면 게이트 없는 `prepareFromAdmissionQueue` 로, 아니면 `prepare` 로 위임. 분리 코어 `doPrepare` 공유.
- **보상통제**: Reconcile `warnRequested` 가 REQUESTED-stuck ∧ admission 활성 ∧ ZSET 미포함이면 `parkIfAbsent` 재park(Redrive — drainer 가 재발행). park→markProcessed 로 Kafka 가 끊긴 워크플로우의 유일 안전망.

### 양보 불가 (NON-NEGOTIABLE)

1. 재발행 messageId ≠ 원본 parked messageId. 원본은 park 시 markProcessed 되어 재사용 시 `isProcessed` 컷으로 전량 silent skip. 형식 `AdmissionDrainMessageId` = `model-generation:drain:{workflowId}:{dispatchEpochMillis}`.
2. `dispatchEpochMillis` 는 drain-lock 안 1회 생성·payload 박제·재계산 금지. `@RetryableTopic` 재시도 간 동일 유지(dedupe·DLT 종결 정상).
3. Kafka 재발행은 drain-lock 밖 + async only. lock 안 `send()` 금지, `.get()` 금지. producer `max.block.ms=5000`(미설정 기본 60s 스레드 점유 방지).
4. **in-flight marker 도입 안 함(옵션 c)**. 이중 메시지는 `REQUESTED→PREPARING` 조건부 UPDATE(ADR-012) 가 최종 방어. **이 수용의 전제**: (i) Reconcile 재park 활성(prod `app.reconcile.enabled=true`) **그리고** (ii) 조건부 UPDATE — 둘 다. `admission.enabled=false`(롤백) 면 게이트도 비활성 → park 자체 없음 → 일관.
5. `bypassAdmission=true` 는 admission 게이트만 우회. `validateSourceImages`/circuit/`tripo:semaphore`/pending/조건부 전이는 전부 그대로.
6. FIFO = **입장 선발 순서**(완료 순서 아님). 재발행 partition key = `String.valueOf(showcaseId)`(최초 발행과 동일).
7. `app.reconcile.requested-stuck-seconds` 30→**300**. @RetryableTopic 첫 backoff(30s)+Kafka 왕복 동안 재발행분이 REQUESTED 인데 30s 면 Reconcile 이 곧장 재park → 재시도 체인 증폭. **이 노브는 Outbox-relay-stuck 탐지지연 ↔ admission in-flight 오탐억제 두 역할을 멀티플렉싱**하며 상향 시 전자 지연도 커진다. 정공법은 D1(옵션 b).
8. async send 실패는 콜백 별도 처리 안 함(Reconcile 재park 가 단일 보상). 단 `republishFailed` 카운터+WARN 으로 관측(silent drop 오인 방지).
9. 드레이너 tick 당 `available = maxConcurrent - countActive` 개만 1회 pop, 재루프 없음(스케줄러 스레드 점유 유계).
10. drain-lock(`tripo:queue:drain-lock`, Redisson lease 자동해제) 스코프 = countActive+pop+REQUESTED확인+목록구성까지. 외부 I/O 절대 락 밖. 드레이너 빈 조건 = `@ConditionalOnExpression(spring.kafka.enabled AND admission.enabled)`(kafka 비활성 시 `kafkaTemplate` 부재로 컨텍스트 붕괴 방지).

## 3. 고려한 대안 (Alternatives)

### A. inline-drain (폐기 PR #95) — **기각**

드레이너가 `prepare()` 를 인라인 호출. 코드 리뷰에서 확정 결함:
- **Critical**: Tripo 업로드/`POST`/세마포어 블로킹이 Spring Boot 기본 단일 스케줄러 스레드(OutboxRelay 1s·Reconcile 60s 공유) 점유 → 피크 시 OutboxRelay·Reconcile(이 기능의 안전망) 동시 기아. 2026-04-28 사고 클래스 재현.
- **H1**: park→markProcessed 로 Kafka 소멸 → drained 항목이 `@RetryableTopic` exp backoff/DLT 우회.
- **H2**: pop 후 `admitOrPark` 재평가 시 `parkIfAbsent(now)` score 리셋 → 부하 시 FIFO 붕괴·S3 HEAD 증폭.
- poison(park↔drain↔fail) 무종결.

본 결정(재발행) 은 드레이너에서 Tripo I/O 를 제거하고 실행을 Worker(`@RetryableTopic`/DLT) 로 되돌려 Critical/H1/H2/poison-종결 을 동시에 닫는다.

### B. in-flight marker (TTL Redis 키) — **기각(옵션 c 채택)**

drainer pop~PREPARING 윈도 동안 Reconcile 중복 재park 를 marker 로 억제. 그러나 단일 정적 TTL 이 (i) @RetryableTopic ~15분 retry 윈도 억제 ↔ (ii) drainer 크래시 빠른 회복 이라는 상반된 두 요구를 동시에 만족 못 함(marker TTL 충돌). marker 제거(옵션 c) + 조건부 UPDATE 최종 방어 + `requested-stuck-seconds` 상향(NN7) 으로 단순·안전하게 수용. 정밀화는 D1.

### C. submit 시점 분기 / 게이트를 Worker 레벨 / 장수명 permit / 하드캡 — **기각**

(폐기 ADR 1차 §3 동일 판단 승계: Kafka 전송 SoT 우월 / validate 뒤가 정확 / permit 누수 / 과대 비용.) 하드캡(대안 E)은 D3.

## 4. 결과 (Consequences)

### 긍정
- 동시 작업 수가 cap 근처 제어. DB 가 SoT — 워커 사망/재시작/누수 자가 치유.
- Kafka 전송 보장(outbox·재시도·순서·DLT) 유지. 스케줄러 스레드 점유 없음(드레이너는 저렴한 send 만, lock 밖 async).
- `poll:delayed-queue`·`tripo:semaphore` 무변경. 롤백 = 설정 한 줄(`admission.enabled=false`, 단 **재시작 동반** — 빈 등록 조건). 스키마 변경 0.

### 부정 / 리스크
- soft cap: `countActive()`–전이 비원자 → 동시 Worker race 로 cap 소폭 초과 가능(`tripo:semaphore` 가 Tripo rate 하드 제한).
- N1: marker 미도입으로 drainer pop~PREPARING 윈도에 Reconcile 중복 재park → 이중 메시지 가능(correctness 는 조건부 UPDATE 가 방어, `bypassSkipped` 로 관측). `requested-stuck-seconds`(NN7) 로 윈도 통제.
- drain latency: fixedDelay 주기(기본 5s) 만큼 재투입 지연. 재발행분 `validateSourceImages` 재실행(S3 HEAD 4회).

### 관측 (Counters — 본 PR 필수, 대시보드는 P1-H deferred)
`AdmissionMetricsPort`(application) + `MicrometerAdmissionMetricsAdapter`(adapter, archTest 가 application→micrometer 직접 의존 금지):
`gearshow.admission.park.count` / `.drain.dispatched.count` / `.queue.size`(gauge) / `.bypass.skipped.count` / `.republish.failed.count` / `.inflight`(Timer = dispatch→입장 지연, **NN7 직접 판정 지표**). defer-until-measured(D1/D3) 트리거 측정 수단이라 deferred 아님.

### Deferred (백로그)
- **D1**: N1 정밀화 → 옵션 (b) DB `dispatched_at`/`ADMITTED` 상태. 트리거 = 멀티인스턴스 ∨ 지속부하 ∨ Outbox-stuck 탐지지연 — 셋 중 먼저.
- **D2**: 큐 무한 대기 — 영구 포화 시 parked 항목은 `contains=true` 라 Reconcile 종결 안 함(DLT/timeout 없이 무기한). `max-queue-age→markFailed` + 사용자 큐 위치 피드백(1차 리뷰 M3 계열).
- **D3**: 하드 캡(원자적 reservation). 피크 실측 시.
- **D4**: 재발행 포트 추출 — 현재 `AdmissionQueueDrainScheduler`(in 어댑터) 가 `KafkaTemplate` 직접 사용. `AdmissionRepublishPort`(out) + KafkaAdmissionRepublishAdapter 로 분리하면 드레이너는 슬롯 계산 정책만 남고 `AdmissionDrainMessageId` 도 어댑터로 흡수. archTest 가 못 잡는 책임 배치(인바운드가 아웃바운드 I/O 보유) — 내부 architecture-review M-A1. Surgical 원칙상 본 PR 범위 밖, deferred refactor.
- **D5**: `gearshow.admission.republish.failed.count` 및 REQUESTED-stranded(≤`requested-stuck-seconds`=300s 정체) 운영 알람 임계 — 단일 보상 경로(Reconcile 재park)에 회복 SLA 가 전적 의존하므로 5분 stranded 누적을 조용히 흘리지 않도록 알람 필요. 내부 architecture-review M-A2. P1-H 관찰성 작업에 포함.
- **D6**: drain discard(pop 후 REQUESTED 아님) 관측 카운터 — defer-until-measured 의 측정 공백(내부 code-review M-C1). 단일 MySQL(복제 없음) 에선 stale-mask 손실이 near-theoretical 이라 본 PR 무계측 수용; 멀티 인스턴스/읽기 복제 도입(D1 트리거) 시 함께 계측.

### 검증
- 단위: 게이트 5분기(`PrepareWorkflowServiceTest$AdmissionGate`) / 라우팅 3(`ModelGenerationWorkerTest$AdmissionRouting`) / 드레이너 6(`AdmissionQueueDrainSchedulerTest`) / Reconcile 재park 3(`...$RequestedRepark`).
- `./gradlew build` 전체 통과(컴파일+테스트+JaCoCo 70%+ArchUnit).

## 5. 참조
- 설계 문서 §3.4(Redis 키 — `tripo:queue`·`tripo:queue:drain-lock` 정식화) · §6.6/§8.4(Reconcile 보상통제) · §7[5](게이트+재발행 시퀀스) · §14(v1.3)
- ADR-012(조건부 UPDATE — N1 최종 방어), ADR-013(Redis 필수 — ZSET/lock 어댑터 무조건 빈), ADR-011(markProcessed 시점 — park 사슬 전제; 단 §2② 는 코드가 SoT)
- 패턴: Admission Control, Redrive(Reconcile→drainer 위임), Kafka retry-topic backoff/DLT 재사용, Soft Concurrency Cap
