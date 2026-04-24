package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.event.TripoSuccessEvent;
import com.gearshow.backend.showcase.application.port.in.PollWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationStatus;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoSemaphorePort;
import com.gearshow.backend.showcase.infrastructure.config.TripoPollingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 단일 워크플로우의 Tripo 상태를 한 번 폴링하는 서비스 (설계 §6.2, §7).
 *
 * <p>Redis DelayedQueue 에서 방출된 workflowId 마다 호출된다. Tripo semaphore 획득 후
 * {@code fetchStatus} 를 실행하고, phase 에 따라 DB 를 조건부 UPDATE 한다 (ADR-012).</p>
 *
 * <p><b>락 사용 금지</b> (설계 §6.1): Poller 는 {@code workflow:lock} 을 사용하지 않는다.
 * 상태 전이는 모두 WHERE {@code current_step = GENERATING AND tripo_succeeded_at IS NULL}
 * 조건부 UPDATE 로 race 를 차단한다.</p>
 *
 * <p><b>중복 이벤트 방지</b>: {@link TripoSuccessEvent} 는 {@code markTripoSucceeded}
 * affected=1 인 경우에만 발행한다. 같은 task_id 에 대해 두 번 호출돼도 두 번째는
 * affected=0 이 되어 조용히 스킵된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PollWorkflowService implements PollWorkflowUseCase {

    private static final String FAILURE_SOURCE_TRIPO = "TRIPO_API";

    private final ModelGenerationWorkflowPort workflowPort;
    private final ModelGenerationClient modelGenerationClient;
    private final TripoSemaphorePort tripoSemaphorePort;
    private final ApplicationEventPublisher eventPublisher;
    private final TripoPollingProperties properties;

    @Override
    public PollOutcome poll(Long workflowId) {
        Optional<WorkflowSnapshot> found = workflowPort.findSnapshot(workflowId);
        if (found.isEmpty()) {
            log.warn("워크플로우 조회 실패 — skip. workflowId: {}", workflowId);
            return PollOutcome.SKIPPED;
        }
        WorkflowSnapshot snapshot = found.get();
        if (snapshot.currentStep() != WorkflowStep.GENERATING) {
            log.info("GENERATING 아님 — skip. workflowId: {}, currentStep: {}",
                    workflowId, snapshot.currentStep());
            return PollOutcome.SKIPPED;
        }
        if (snapshot.tripoSucceededAt() != null) {
            log.info("이미 Tripo SUCCESS 마킹됨 — skip. workflowId: {}", workflowId);
            return PollOutcome.SKIPPED;
        }
        String taskId = snapshot.tripoTaskId();
        if (taskId == null || taskId.isBlank()) {
            log.warn("tripo_task_id 가 비어있음 — Reconcile 대기. skip. workflowId: {}", workflowId);
            return PollOutcome.SKIPPED;
        }

        GenerationStatus status = tripoSemaphorePort.runWithPermit(
                Duration.ofMillis(properties.semaphoreAcquireTimeoutMs()),
                () -> modelGenerationClient.fetchStatus(taskId));

        if (status.isRunning()) {
            int polled = workflowPort.markPolled(workflowId);
            if (polled == 0) {
                log.info("markPolled affected=0 — 다른 주체가 전이함. skip. workflowId: {}", workflowId);
                return PollOutcome.SKIPPED;
            }
            return PollOutcome.IN_PROGRESS;
        }
        if (status.isSuccess()) {
            int marked = workflowPort.markTripoSucceeded(workflowId);
            if (marked == 0) {
                log.info("markTripoSucceeded affected=0 — 중복 이벤트 방지. skip. workflowId: {}",
                        workflowId);
                return PollOutcome.SKIPPED;
            }
            log.info("Tripo SUCCESS — 이벤트 발행. workflowId: {}, taskId: {}", workflowId, taskId);
            eventPublisher.publishEvent(new TripoSuccessEvent(workflowId, taskId));
            return PollOutcome.SUCCEEDED;
        }
        // FAILED
        String reason = status.failureReason();
        log.warn("Tripo FAILED — markFailed 전이. workflowId: {}, taskId: {}, reason: {}",
                workflowId, taskId, reason);
        int failed = workflowPort.markFailed(
                workflowId, WorkflowFailureCode.TRIPO_TASK_FAILED, reason, FAILURE_SOURCE_TRIPO);
        if (failed == 0) {
            log.info("markFailed affected=0 — 이미 종결된 워크플로우. workflowId: {}", workflowId);
        }
        return PollOutcome.FAILED;
    }
}
