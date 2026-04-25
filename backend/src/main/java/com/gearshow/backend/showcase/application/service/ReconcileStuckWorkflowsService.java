package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.StuckWorkflow;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.event.TripoSuccessEvent;
import com.gearshow.backend.showcase.application.event.WorkflowGeneratingConfirmedEvent;
import com.gearshow.backend.showcase.application.port.in.ReconcileStuckWorkflowsUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.ReconcileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stuck 워크플로우 복구 서비스 (설계 §8.4).
 *
 * <p><b>복구 전략 — Redrive</b>: Reconcile 은 상태를 직접 되돌리는 대신 기존 파이프라인(Poller /
 * Downloader / Worker) 의 진입점을 재활성화한다. 락 내부에서 DB 상태를 보존하고, 외부 I/O (Tripo /
 * S3) 는 이후 단계의 리스너가 락 밖에서 수행하게 한다. 이 구조는 (a) 락 점유 시간을 ms 수준으로
 * 유지하고 (b) 외부 호출 로직을 중복 구현하지 않는다.</p>
 *
 * <p>복구 액션:</p>
 * <ul>
 *   <li><b>PREPARING stuck</b>: {@code tripo_pending_task} 에 task_id 가 있으면 {@code markGenerating} 후
 *       pending 정리 + {@code WorkflowGeneratingConfirmedEvent} 재발행으로 Poller DelayedQueue 에 offer.
 *       없으면 {@link WorkflowFailureCode#TX2_DB_FAILED} 로 FAILED (task_id 유실 — 이중 과금 위험 존재).</li>
 *   <li><b>GENERATING + tripo_succeeded_at IS NULL</b>: {@link WorkflowPollQueuePort#offer} 로 즉시 재폴링.
 *       Poller 가 Tripo GET 을 재호출해 SUCCESS/FAILED 에 따라 {@code markTripoSucceeded} 또는
 *       {@code markFailed} 를 수행.</li>
 *   <li><b>GENERATING + tripo_succeeded_at IS NOT NULL</b>: {@code TripoSuccessEvent} 재발행.
 *       Downloader 가 Tripo GET + S3 mirror + TX_final 을 다시 시도.</li>
 *   <li><b>REQUESTED stuck</b>: 경고 로그만. Outbox Relay 점검 신호.</li>
 * </ul>
 *
 * <p><b>락 정책 (ADR-012)</b>: Reconcile 은 Worker/Downloader 와 동일한 {@code workflow:lock:{id}}
 * 를 공유한다. 락 busy 시 조용히 skip — 살아있는 주체를 덮어쓰지 않는다. 락 획득 후 heartbeat 재검증으로
 * stuck 오판을 한 번 더 방어한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.reconcile", name = "enabled", havingValue = "true")
public class ReconcileStuckWorkflowsService implements ReconcileStuckWorkflowsUseCase {

    private static final String FAILURE_SOURCE_SCHEDULER = "SCHEDULER";

    private final ModelGenerationWorkflowPort workflowPort;
    private final TripoPendingTaskPort tripoPendingTaskPort;
    private final WorkflowLockPort workflowLockPort;
    private final WorkflowPollQueuePort workflowPollQueuePort;
    private final ApplicationEventPublisher eventPublisher;
    private final ReconcileProperties properties;

    @Override
    public int reconcileOnce() {
        int tried = 0;
        tried += reconcilePreparing();
        tried += reconcileGeneratingTripo();
        tried += reconcileGeneratingS3();
        tried += warnRequested();
        return tried;
    }

    private int reconcilePreparing() {
        Instant threshold = Instant.now().minusSeconds(properties.preparingStuckSeconds());
        List<StuckWorkflow> stuck = workflowPort.findStuckPreparing(threshold, properties.batchSize());
        for (StuckWorkflow w : stuck) {
            recoverPreparing(w, threshold);
        }
        return stuck.size();
    }

    private void recoverPreparing(StuckWorkflow w, Instant heartbeatThreshold) {
        AtomicBoolean resumed = new AtomicBoolean(false);
        try {
            workflowLockPort.withLock(w.id(), () -> {
                Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(w.id());
                if (snapshot.isEmpty()) {
                    return;
                }
                WorkflowSnapshot s = snapshot.get();
                if (s.currentStep() != WorkflowStep.PREPARING) {
                    return;
                }
                if (s.heartbeatAt() != null && s.heartbeatAt().isAfter(heartbeatThreshold)) {
                    log.debug("PREPARING heartbeat 복구됨 — skip. workflowId: {}", w.id());
                    return;
                }
                Optional<String> pendingTaskId =
                        tripoPendingTaskPort.findTaskIdByWorkflowId(w.id());
                if (pendingTaskId.isPresent()) {
                    int affected = workflowPort.markGenerating(w.id(), pendingTaskId.get());
                    if (affected > 0) {
                        tripoPendingTaskPort.deleteByWorkflowId(w.id());
                        resumed.set(true);
                        log.info("Reconcile PREPARING → GENERATING 재개. workflowId: {}, taskId: {}",
                                w.id(), pendingTaskId.get());
                    }
                } else {
                    int affected = workflowPort.markFailed(
                            w.id(),
                            WorkflowFailureCode.TX2_DB_FAILED,
                            "PREPARING stuck + tripo_pending_task 없음 — Reconcile 복구 불가",
                            FAILURE_SOURCE_SCHEDULER);
                    if (affected > 0) {
                        log.warn("ALERT: Reconcile PREPARING → FAILED (task_id 유실). workflowId: {}",
                                w.id());
                    }
                }
            });
            // 락 밖 후속 — PREPARING → GENERATING 전이에 성공한 경우에만 Poller 재진입 트리거.
            // affected=0, heartbeat 복구, snapshot 없음, FAILED 경로 모두 이벤트 미발행으로 FAILED 워크플로우
            // 재큐나 중복 Tripo GET 을 차단한다 (ADR-012 조건부 UPDATE affected 기준 판정).
            if (resumed.get()) {
                eventPublisher.publishEvent(new WorkflowGeneratingConfirmedEvent(w.id()));
            }
        } catch (WorkflowLockBusyException e) {
            log.debug("PREPARING stuck — 락 busy, skip. workflowId: {}", w.id());
        } catch (DataAccessException e) {
            log.error("PREPARING stuck 복구 실패 - workflowId: {}", w.id(), e);
        }
    }

    private int reconcileGeneratingTripo() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.generatingTripoStuckMinutes()));
        List<StuckWorkflow> stuck =
                workflowPort.findStuckGeneratingTripo(threshold, properties.batchSize());
        for (StuckWorkflow w : stuck) {
            try {
                // Poller 재등록. Tripo GET 과 상태 전이는 Poller 가 담당 (락 안 필요).
                workflowPollQueuePort.offer(w.id(), Duration.ZERO);
                log.info("Reconcile GENERATING·Tripo stuck → DelayedQueue 재등록. workflowId: {}",
                        w.id());
            } catch (RuntimeException e) {
                log.error("GENERATING·Tripo stuck 재큐 실패 - workflowId: {}", w.id(), e);
            }
        }
        return stuck.size();
    }

    private int reconcileGeneratingS3() {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(properties.generatingS3StuckMinutes()));
        List<StuckWorkflow> stuck =
                workflowPort.findStuckGeneratingS3(threshold, properties.batchSize());
        for (StuckWorkflow w : stuck) {
            recoverGeneratingS3(w, threshold);
        }
        return stuck.size();
    }

    /**
     * GENERATING·S3 stuck 을 Downloader 에 재위임한다.
     *
     * <p>외부 I/O 중복 실행 방지를 위해 락 획득 후 snapshot 재조회로 다음을 재검증한다:</p>
     * <ol>
     *   <li>currentStep = GENERATING 유지 (이미 COMPLETED/FAILED 면 skip)</li>
     *   <li>tripoSucceededAt != null (Poller 성공 인지 여부)</li>
     *   <li>heartbeat_at 이 여전히 임계 이전 (살아있는 Downloader 오판 방지)</li>
     * </ol>
     * 재검증 통과 시에만 락 밖에서 {@link TripoSuccessEvent} 를 재발행한다. 이벤트 발행은 비동기 Downloader
     * 를 깨우는 용도일 뿐이고, Downloader 진입 시 `workflow:lock` 을 재획득하므로 여기서는 락을 곧장
     * 해제한다 (Reconcile 자체가 Tripo GET/S3 PUT 을 돌리지 않음).
     */
    private void recoverGeneratingS3(StuckWorkflow w, Instant heartbeatThreshold) {
        if (w.tripoTaskId() == null) {
            log.error("ALERT: GENERATING·S3 stuck 인데 tripo_task_id 없음. workflowId: {}", w.id());
            return;
        }
        AtomicBoolean republish = new AtomicBoolean(false);
        try {
            workflowLockPort.withLock(w.id(), () -> {
                Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(w.id());
                if (snapshot.isEmpty()) {
                    return;
                }
                WorkflowSnapshot s = snapshot.get();
                if (s.currentStep() != WorkflowStep.GENERATING) {
                    log.debug("GENERATING·S3 재검증 — currentStep 변경됨. skip. workflowId: {}, step: {}",
                            w.id(), s.currentStep());
                    return;
                }
                if (s.tripoSucceededAt() == null) {
                    log.debug("GENERATING·S3 재검증 — tripoSucceededAt 미인지. skip. workflowId: {}",
                            w.id());
                    return;
                }
                if (s.heartbeatAt() != null && s.heartbeatAt().isAfter(heartbeatThreshold)) {
                    log.debug("GENERATING·S3 heartbeat 복구됨 — skip. workflowId: {}", w.id());
                    return;
                }
                republish.set(true);
            });
            if (republish.get()) {
                eventPublisher.publishEvent(new TripoSuccessEvent(w.id(), w.tripoTaskId()));
                log.info("Reconcile GENERATING·S3 stuck → Downloader 재시작. workflowId: {}", w.id());
            }
        } catch (WorkflowLockBusyException e) {
            log.debug("GENERATING·S3 stuck — 락 busy, skip. workflowId: {}", w.id());
        } catch (DataAccessException e) {
            log.error("GENERATING·S3 stuck 복구 실패 - workflowId: {}", w.id(), e);
        }
    }

    private int warnRequested() {
        Instant threshold = Instant.now().minusSeconds(properties.requestedStuckSeconds());
        List<StuckWorkflow> stuck =
                workflowPort.findStuckRequested(threshold, properties.batchSize());
        for (StuckWorkflow w : stuck) {
            log.warn("ALERT: REQUESTED stuck — Outbox Relay 점검 필요. workflowId: {}, createdAt: {}",
                    w.id(), w.createdAt());
        }
        return 0;
    }
}
