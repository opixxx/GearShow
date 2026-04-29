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
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
 *
 * <p><b>활성화 조건 (AND)</b>: {@code app.reconcile.enabled=true} 와 {@code gearshow.redis.enabled=true}
 * 가 모두 참일 때만 Bean 으로 등록된다. Reconcile 의 핵심 동작인 {@link WorkflowPollQueuePort#offer}
 * 재등록이 Redis(Redisson DelayedQueue) 의존이기 때문 — 둘 중 하나라도 누락되면 {@code WorkflowPollQueuePort}
 * 빈 자체가 미등록이라 DI 가 실패한다. AND 조건으로 묶어 두 플래그 모순 자체를 차단한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.reconcile.enabled:false} and ${gearshow.redis.enabled:false}")
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
        Instant now = Instant.now();
        Instant threshold = now.minus(Duration.ofMinutes(properties.generatingTripoStuckMinutes()));
        long capMinutes = properties.workflowMaxLifetimeMinutes();
        Instant capThreshold = now.minus(Duration.ofMinutes(capMinutes));
        List<StuckWorkflow> stuck =
                workflowPort.findStuckGeneratingTripo(threshold, properties.batchSize());
        for (StuckWorkflow w : stuck) {
            recoverGeneratingTripo(w, capThreshold, capMinutes);
        }
        return stuck.size();
    }

    /**
     * GENERATING-Tripo stuck 워크플로우 1건을 복구한다.
     *
     * <p>lifetime cap 초과 시 락 + snapshot 재검증으로 살아있는 Poller 와의 race 를 차단한 뒤
     * {@code markFailed(TRIPO_TIMEOUT_GENERATING)} 로 강제 손절한다. cap 미초과 시 기존
     * 폴링 재등록(redrive) 경로로 진행한다.</p>
     */
    private void recoverGeneratingTripo(StuckWorkflow w, Instant capThreshold, long capMinutes) {
        if (exceedsLifetimeCap(w, capThreshold)) {
            tryFailOnLifetimeCap(w, capMinutes);
            return;
        }
        redriveToPoller(w);
    }

    /**
     * started_at 기준 cap 초과 여부. {@code started_at IS NULL} 은 방어적으로 cap 미적용 —
     * REQUESTED→PREPARING 전이 시 초기화되므로 정상 흐름의 GENERATING 상태에선 항상 NOT NULL.
     * 데이터 이상 시에는 기존 redrive 동작을 유지해 안전한 fallback 을 보장한다.
     */
    private boolean exceedsLifetimeCap(StuckWorkflow w, Instant capThreshold) {
        return w.startedAt() != null && w.startedAt().isBefore(capThreshold);
    }

    /**
     * 락 획득 후 snapshot 재검증을 거쳐 {@code markFailed} 를 호출한다.
     *
     * <p>{@code recoverPreparing}/{@code recoverGeneratingS3} 와 동일한 락 + 재검증 패턴.
     * 살아있는 Poller 가 직전에 {@code tripoSucceededAt} 을 갱신했거나 워크플로우가 이미
     * 종결된 경우 cap 발동을 보류해 false-positive (정상 SUCCESS 결과 폐기) 를 방지한다.
     * 락 busy 면 다음 사이클(60초 후) 에 재시도하므로 안전한 폴백이 된다.</p>
     */
    private void tryFailOnLifetimeCap(StuckWorkflow w, long capMinutes) {
        try {
            workflowLockPort.withLock(w.id(), () -> {
                Optional<WorkflowSnapshot> snapshot = workflowPort.findSnapshot(w.id());
                if (snapshot.isEmpty()) {
                    log.debug("GENERATING·Tripo cap — snapshot 없음, skip. workflowId: {}", w.id());
                    return;
                }
                WorkflowSnapshot s = snapshot.get();
                if (s.currentStep() != WorkflowStep.GENERATING) {
                    log.debug("GENERATING·Tripo cap — currentStep 변경됨, skip. "
                                    + "workflowId: {}, step: {}",
                            w.id(), s.currentStep());
                    return;
                }
                if (s.tripoSucceededAt() != null) {
                    log.info("GENERATING·Tripo cap 보류 — Poller 가 직전에 SUCCESS 인지함. "
                            + "workflowId: {}", w.id());
                    return;
                }
                String message = String.format(
                        "lifetime cap 초과 — Tripo polling 무한 redrive 차단 (cap=%d분)",
                        capMinutes);
                int affected = workflowPort.markFailed(
                        w.id(),
                        WorkflowFailureCode.TRIPO_TIMEOUT_GENERATING,
                        message,
                        FAILURE_SOURCE_SCHEDULER);
                if (affected > 0) {
                    log.warn("ALERT: Reconcile lifetime cap 발동 → FAILED. "
                                    + "workflowId: {}, startedAt: {}, capMinutes: {}",
                            w.id(), w.startedAt(), capMinutes);
                } else {
                    log.info("Reconcile lifetime cap markFailed affected=0 — "
                            + "이미 종결된 워크플로우. workflowId: {}", w.id());
                }
            });
        } catch (WorkflowLockBusyException e) {
            log.debug("GENERATING·Tripo cap — 락 busy, 다음 사이클에 재시도. workflowId: {}", w.id());
        } catch (DataAccessException e) {
            log.error("GENERATING·Tripo cap markFailed 실패 - workflowId: {}", w.id(), e);
        }
    }

    private void redriveToPoller(StuckWorkflow w) {
        try {
            // Poller 재등록. Tripo GET 과 상태 전이는 Poller 가 담당 (락 안 필요).
            workflowPollQueuePort.offer(w.id(), Duration.ZERO);
            log.info("Reconcile GENERATING·Tripo stuck → DelayedQueue 재등록. workflowId: {}",
                    w.id());
        } catch (RuntimeException e) {
            log.error("GENERATING·Tripo stuck 재큐 실패 - workflowId: {}", w.id(), e);
        }
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
