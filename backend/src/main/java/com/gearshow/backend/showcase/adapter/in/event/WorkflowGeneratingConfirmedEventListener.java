package com.gearshow.backend.showcase.adapter.in.event;

import com.gearshow.backend.showcase.application.event.WorkflowGeneratingConfirmedEvent;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.TripoPollingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/**
 * Worker 가 TX2 성공 후 발행한 {@link WorkflowGeneratingConfirmedEvent} 를 구독해 Redis
 * DelayedQueue 에 워크플로우 id 를 offer 한다 (설계 §6.2 — 30s delay).
 *
 * <p><b>@TransactionalEventListener(AFTER_COMMIT)</b>: 현재 Worker 경로에 서비스 레벨 TX 는
 * 없지만 {@code fallbackExecution=true} 로 설정해 TX 없는 발행도 수신한다. 향후 Worker 에
 * {@code @Transactional} 이 추가되어도 "DB 커밋 후에만 offer" 계약이 유지된다.</p>
 *
 * <p><b>@Async("tripoPollingExecutor")</b>: Redis offer 는 블로킹 호출이지만 저비용이라 기존
 * Poller 전용 실행기를 재사용한다 (별도 풀을 늘릴 필요 없음).</p>
 *
 * <p><b>Redis disabled 환경</b>: {@link WorkflowPollQueuePort} Bean 이 없으므로
 * {@link ObjectProvider#getIfAvailable()} 로 안전 조회 — 단일 인스턴스 로컬/테스트에서는
 * 조용히 no-op 된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowGeneratingConfirmedEventListener {

    private final ObjectProvider<WorkflowPollQueuePort> pollQueueProvider;
    private final TripoPollingProperties pollingProperties;

    @Async("tripoPollingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onConfirmed(WorkflowGeneratingConfirmedEvent event) {
        WorkflowPollQueuePort pollQueue = pollQueueProvider.getIfAvailable();
        if (pollQueue == null) {
            log.debug("WorkflowPollQueuePort 미등록 — Poller 비활성 환경 (Redis disabled). "
                    + "skip offer. workflowId: {}", event.workflowId());
            return;
        }
        Duration delay = Duration.ofSeconds(pollingProperties.delaySeconds());
        try {
            pollQueue.offer(event.workflowId(), delay);
            log.info("Poller DelayedQueue 등록 — workflowId: {}, delay: {}",
                    event.workflowId(), delay);
        } catch (RuntimeException e) {
            log.error("Poller DelayedQueue 등록 실패 — Reconcile 이 복구 예정. workflowId: {}",
                    event.workflowId(), e);
        }
    }
}
