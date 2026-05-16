package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 입장 큐({@code tripo:queue}) drainer (ADR-025).
 *
 * <p>{@code PrepareWorkflowService} 의 입장 게이트가 cap 초과로 park 한 워크플로우는
 * {@code markProcessed} 로 Kafka 재전송이 끊긴 상태다. 본 스케줄러가 슬롯 여유
 * ({@code countActive < maxConcurrent}) 시 가장 오래된 항목부터 꺼내 prepare 를 재투입한다.</p>
 *
 * <p><b>tick 당 상한</b>: 한 사이클에 최대 {@code maxConcurrent * 2} 건만 처리한다. 상한이 없으면
 * 큐가 길 때 스케줄러 스레드를 장시간 점유해 다른 {@code @Scheduled} 가 starvation 된다.</p>
 *
 * <p><b>pop 후 재검증</b>: {@link TripoAdmissionQueuePort#pollOldest()} 로 꺼낸 뒤 워크플로우가
 * 여전히 {@code REQUESTED} 일 때만 재투입한다. 다른 경로(재전송/Reconcile)가 이미 진행시켰으면
 * skip — 이중 처리·좀비 방어. 한 원소 실패는 흡수하고 다음 원소를 계속한다(Reconcile 이
 * REQUESTED-stranded 최종 복구).</p>
 *
 * <p><b>재투입은 게이트를 다시 통과</b>: {@code prepare()} 는 입장 게이트를 재평가한다(방어 심층).
 * 드물게 soft-cap race 로 재-park 되면 score 가 갱신돼 FIFO 위치가 뒤로 밀릴 수 있다 —
 * ADR-025 가 수용한 soft 특성으로, 현 규모에서 starvation 수준은 아니다.</p>
 *
 * <p>활성화 조건: {@code app.model-generation.admission.enabled=true}(기본값). {@code false}
 * (롤백 스위치) 면 게이트가 park 하지 않으므로 drainer 도 등록하지 않는다. Redis 는 필수
 * 인프라(ADR-013) 이므로 {@code TripoAdmissionQueuePort} 는 항상 빈으로 존재한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.model-generation.admission.enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AdmissionQueueDrainScheduler {

    private final TripoAdmissionQueuePort admissionQueuePort;
    private final ModelGenerationWorkflowPort workflowPort;
    private final PrepareWorkflowUseCase prepareWorkflowUseCase;
    private final AdmissionQueueProperties properties;

    @Scheduled(fixedDelayString = "${app.model-generation.admission.drain-interval-ms:5000}")
    public void drain() {
        int cap = properties.maxConcurrent();
        int limit = cap * 2;
        int dispatched = 0;
        try {
            // 매 루프 countActive 재조회 — 재투입(dispatch)이 PREPARING 전이로 활성 수를
            // 늘리면 즉시 멈춰 soft-cap 누수를 최소화한다. 루프 밖 호이스팅 금지(stale 카운트로 과투입).
            while (dispatched < limit && workflowPort.countActive() < cap) {
                Optional<Long> next = admissionQueuePort.pollOldest();
                if (next.isEmpty()) {
                    break;
                }
                dispatched++;
                dispatch(next.get());
            }
        } catch (Exception e) {
            log.error("입장 큐 drain 사이클 예외", e);
        }
        if (dispatched > 0) {
            log.info("입장 큐 drain 완료 — dispatched: {}, 잔여 대기: {}",
                    dispatched, admissionQueuePort.size());
        }
    }

    private void dispatch(long workflowId) {
        try {
            Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(workflowId);
            WorkflowStep step = snapshot.map(WorkflowSnapshot::currentStep).orElse(null);
            if (step != WorkflowStep.REQUESTED) {
                log.info("drain skip — REQUESTED 아님(다른 경로 처리됨). workflowId: {}, step: {}",
                        workflowId, step);
                return;
            }
            log.info("drain 재투입 — workflowId: {}", workflowId);
            prepareWorkflowUseCase.prepare(workflowId);
        } catch (RuntimeException e) {
            // 한 원소 실패가 전체 drain 을 막지 않도록 흡수. Reconcile 이 REQUESTED-stranded 최종 복구.
            log.error("drain 재투입 실패 — Reconcile 복구 예정. workflowId: {}", workflowId, e);
        }
    }
}
