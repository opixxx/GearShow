package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.adapter.out.messaging.AdmissionDrainMessageId;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.out.AdmissionDrainLockPort;
import com.gearshow.backend.showcase.application.port.out.AdmissionMetricsPort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import com.gearshow.backend.showcase.infrastructure.config.ShowcaseKafkaTopicConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 입장 큐({@code tripo:queue}) drainer (ADR-025, 재발행 방식).
 *
 * <p><b>드레이너는 Tripo I/O 를 절대 하지 않는다</b> — 폐기 PR #95 의 Critical(드레이너가
 * {@code prepare()} 인라인 호출 → Tripo 업로드/POST/세마포어 블로킹이 Spring Boot 기본 단일
 * 스케줄러 스레드[OutboxRelay·Reconcile 공유] 점유) 을 제거한다. 본 드레이너는 슬롯 여유 시
 * ZSET 에서 FIFO pop 후 Kafka 로 {@code bypassAdmission=true} 메시지를 async 재발행만 하고,
 * 실행·재시도·DLT 는 기존 {@code ModelGenerationWorker}(@RetryableTopic) 가 담당한다.</p>
 *
 * <p><b>drain-lock 스코프 (NN3·NN10)</b>: {@code countActive + ZSET pop + REQUESTED 확인 +
 * 대상 목록 구성}까지만 락 안. {@code KafkaTemplate.send} (외부 I/O) 는 반드시 락 밖 + async
 * (.get() 금지). producer {@code max.block.ms=5000} 으로 send() 의 스레드 점유도 캡한다.</p>
 *
 * <p><b>tick 당 상한 (NN9)</b>: {@code available = maxConcurrent - countActive} 개만 1회 pop,
 * 재루프 없음 — 스케줄러 스레드 점유 유계.</p>
 *
 * <p><b>실패 처리 (NN8/C)</b>: async send 실패는 콜백에서 별도 회복하지 않는다 — Reconcile 의
 * REQUESTED-stranded 재park 가 단일 보상 경로다. 단 실패는 카운터+WARN 으로 관측한다.</p>
 *
 * <p>활성화: {@code spring.kafka.enabled=true} <b>그리고</b>
 * {@code app.model-generation.admission.enabled=true}(기본). 드레이너는 Kafka 로 재발행하므로
 * kafka 비활성(단위/통합 테스트 등) 에선 {@code kafkaTemplate} 빈이 없어 등록되면 안 된다
 * (기존 {@code ModelGenerationWorker}·{@code OutboxRelayService} 와 동일한 kafka 가드).
 * {@code admission.enabled=false}(롤백) 면 빈 미등록 → 게이트도 park 안 함 → 도입 전 동작.</p>
 */
@Slf4j
@Component
@ConditionalOnExpression(
        "${spring.kafka.enabled:false} and ${app.model-generation.admission.enabled:true}")
public class AdmissionQueueDrainScheduler {

    /** drain-lock lease — 정상 경로는 즉시 unlock, 보유 인스턴스 사망 시 자동 회수용 마진. */
    private static final Duration DRAIN_LOCK_LEASE = Duration.ofSeconds(30);

    private final AdmissionDrainLockPort drainLockPort;
    private final TripoAdmissionQueuePort admissionQueuePort;
    private final ModelGenerationWorkflowPort workflowPort;
    private final AdmissionQueueProperties properties;
    private final AdmissionMetricsPort metricsPort;
    private final KafkaTemplate<String, ModelGenerationRequestMessage> kafkaTemplate;

    public AdmissionQueueDrainScheduler(
            AdmissionDrainLockPort drainLockPort,
            TripoAdmissionQueuePort admissionQueuePort,
            ModelGenerationWorkflowPort workflowPort,
            AdmissionQueueProperties properties,
            AdmissionMetricsPort metricsPort,
            @Qualifier("kafkaTemplate")
            KafkaTemplate<String, ModelGenerationRequestMessage> kafkaTemplate) {
        this.drainLockPort = drainLockPort;
        this.admissionQueuePort = admissionQueuePort;
        this.workflowPort = workflowPort;
        this.properties = properties;
        this.metricsPort = metricsPort;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.model-generation.admission.drain-interval-ms:5000}")
    public void drain() {
        if (!drainLockPort.tryLock(DRAIN_LOCK_LEASE)) {
            log.debug("drain-lock 미획득 — 다른 인스턴스 처리 중이라 건너뜀");
            return;
        }
        List<DrainTarget> targets = new ArrayList<>();
        try {
            long active = workflowPort.countActive();
            long available = (long) properties.maxConcurrent() - active;
            for (long i = 0; i < available; i++) {
                Optional<Long> next = admissionQueuePort.pollOldest();
                if (next.isEmpty()) {
                    break;
                }
                long workflowId = next.get();
                Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(workflowId);
                WorkflowStep step = snapshot.map(WorkflowSnapshot::currentStep).orElse(null);
                if (step != WorkflowStep.REQUESTED) {
                    // REQUESTED 아님 = (a) 이미 다른 경로가 PREPARING/GENERATING 진행 또는
                    // (b) 이미 종결(COMPLETED/FAILED) — 둘 다 discard 가 정상·의도된 동작이다.
                    // 단일 MySQL(읽기 복제 없음) 이라 stale-mask 로 REQUESTED 가 가려질 여지는
                    // 사실상 없다. 만에 하나 ZSET 에서 제거됐는데 미재발행인 항목이 생기면
                    // Reconcile.warnRequested 가 requested-stuck-seconds(기본 300s) 경과 후
                    // 재park 로 복구한다 (ADR-025 §4 N1 비용 — code-review M-C1).
                    log.info("드레인 건너뜀 — REQUESTED 아님(다른 경로 처리됨/종결). workflowId: {}, step: {}",
                            workflowId, step);
                    continue;
                }
                long dispatchEpochMillis = System.currentTimeMillis();
                String drainMessageId =
                        AdmissionDrainMessageId.build(workflowId, dispatchEpochMillis);
                targets.add(new DrainTarget(workflowId, snapshot.get().showcaseId(), drainMessageId));
            }
        } catch (RuntimeException e) {
            // 한 사이클 예외가 스케줄러 스레드를 죽이지 않도록 흡수. 잔여는 다음 tick/Reconcile.
            // lock/port 호출은 전부 RuntimeException 계열 — checked 는 삼키지 않는다(code-review).
            log.error("입장 큐 drain(lock 구간) 예외", e);
        } finally {
            drainLockPort.unlock();
        }
        // drain-lock 밖: 외부 I/O(Kafka send)는 절대 락 밖 + async (NN3·NN10).
        republish(targets);
    }

    private void republish(List<DrainTarget> targets) {
        if (targets.isEmpty()) {
            return;
        }
        int submitted = 0;
        for (DrainTarget t : targets) {
            ModelGenerationRequestMessage msg = ModelGenerationRequestMessage.ofDrain(
                    t.messageId(), t.workflowId(), t.showcaseId());
            try {
                kafkaTemplate.send(ShowcaseKafkaTopicConfig.MODEL_GENERATION_REQUEST_TOPIC,
                                String.valueOf(t.showcaseId()), msg)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                metricsPort.republishFailed();
                                // 비동기 전송 실패. 의도적으로 콜백에서 복구하지 않는다(ADR-025) —
                                // 회복은 Reconcile 의 REQUESTED-stranded 재park 가 단일 보상 경로다.
                                // 조용한 유실로 오인되지 않도록 카운터+경고만 남긴다.
                                log.warn("입장 큐 재발행(비동기) 실패 — Reconcile 이 복구 예정. "
                                                + "workflowId: {}, messageId: {}",
                                        t.workflowId(), t.messageId(), ex);
                            }
                        });
                submitted++;
            } catch (RuntimeException e) {
                // send() 가 Future 반환 전에 던지는 동기 예외(직렬화 오류/버퍼 만수위/
                // 메타데이터 fetch 실패 등). whenComplete 콜백이 못 잡으므로 여기서 동일하게
                // 실패 처리한다 — 미처리 시 해당 항목은 Reconcile 주기까지 유실된다(CodeRabbit).
                metricsPort.republishFailed();
                log.warn("입장 큐 재발행(동기) 실패 — Reconcile 이 복구 예정. "
                                + "workflowId: {}, messageId: {}",
                        t.workflowId(), t.messageId(), e);
            }
        }
        // dispatched = 재발행 송신 *시도 성공* 건수(동기 throw 제외). 실제 입장 ≈
        // dispatched - republish.failed (ADR-025 Counters). 잔여 큐 깊이는
        // gearshow.admission.queue.size gauge 가 제공(매 tick ZCARD 로그 지양).
        metricsPort.dispatched(submitted);
        log.info("입장 큐 drain 재발행 송신 — 시도: {}, 동기실패: {}",
                submitted, targets.size() - submitted);
    }

    private record DrainTarget(long workflowId, Long showcaseId, String messageId) {
    }
}
