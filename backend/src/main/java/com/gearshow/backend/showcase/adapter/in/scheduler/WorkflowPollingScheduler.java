package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.exception.SemaphoreInterruptedException;
import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;
import com.gearshow.backend.showcase.application.port.in.PollWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.WorkflowPollingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis DelayedQueue 를 소비하는 Poller 루프 (설계 §6.2).
 *
 * <p>{@link ApplicationReadyEvent} 수신 즉시 {@code tripoPollingExecutor} 스레드 한 개가
 * {@link WorkflowPollQueuePort#take()} 블로킹 대기 → {@link PollWorkflowUseCase#poll(Long)}
 * 호출 → IN_PROGRESS 면 30초 re-offer 루프를 무한히 수행한다.</p>
 *
 * <p>재enqueue 정책:</p>
 * <ul>
 *   <li>{@link PollWorkflowUseCase.PollOutcome#IN_PROGRESS}: {@code delaySeconds} 로 재offer</li>
 *   <li>{@link TripoSemaphoreTimeoutException}: 짧은 delay(5s) 로 재offer</li>
 *   <li>기타 예외: 로그만, 재enqueue 안함 (Reconcile 이 stuck 복구)</li>
 *   <li>SKIPPED/SUCCEEDED/FAILED: 재enqueue 없음 (종결)</li>
 * </ul>
 *
 * <p>활성화 조건:</p>
 * <ul>
 *   <li>{@code gearshow.redis.enabled=true} — Redis 인프라가 붙어 있어야 한다. disabled 환경에서는
 *       {@code WorkflowPollQueuePort} Bean 도 없어 전체 Poller 계층이 꺼진다.</li>
 *   <li>{@code app.workflow-polling.scheduler-enabled=true} (기본값) — 어댑터/큐만 띄우고 루프 자체는
 *       끄고 싶은 통합 테스트에서 {@code false} 로 오버라이드한다. 끄면 Redis 어댑터·세마포어는
 *       그대로 살아 있지만 자동 소비자가 없어 테스트가 큐를 단독 사용한다.</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnExpression(
        "${gearshow.redis.enabled:false} and ${app.workflow-polling.scheduler-enabled:true}")
@RequiredArgsConstructor
public class WorkflowPollingScheduler {

    /** 세마포어 획득 실패 시 짧게 재시도 — 다른 permit 반환을 유도. */
    private static final Duration SEMAPHORE_RETRY_DELAY = Duration.ofSeconds(5);

    private final WorkflowPollQueuePort pollQueue;
    private final PollWorkflowUseCase pollUseCase;
    private final WorkflowPollingProperties properties;

    @Async("tripoPollingExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void startLoop() {
        Duration defaultDelay = Duration.ofSeconds(properties.delaySeconds());
        log.info("Workflow Poller 루프 시작 — defaultDelay: {}", defaultDelay);
        while (!Thread.currentThread().isInterrupted()) {
            Long workflowId;
            try {
                workflowId = pollQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Workflow Poller 루프 종료 (interrupted)");
                return;
            }
            handle(workflowId, defaultDelay);
        }
    }

    private void handle(Long workflowId, Duration defaultDelay) {
        try {
            PollWorkflowUseCase.PollOutcome outcome = pollUseCase.poll(workflowId);
            if (outcome == PollWorkflowUseCase.PollOutcome.IN_PROGRESS) {
                pollQueue.offer(workflowId, defaultDelay);
            }
        } catch (SemaphoreInterruptedException e) {
            // 인터럽트는 shutdown 신호 — workflowId 를 delay 0 으로 되돌려 다음 기동이 곧바로 다룰
            // 수 있게 하고 루프를 종료한다. (현재 스레드 interrupt flag 는 어댑터에서 이미 세움)
            log.info("Poller 인터럽트 감지 — 루프 종료. workflowId: {}", workflowId);
            safeReenqueue(workflowId, Duration.ZERO);
            Thread.currentThread().interrupt();
        } catch (TripoSemaphoreTimeoutException e) {
            log.info("Tripo semaphore 획득 실패 — {} 후 재enqueue. workflowId: {}",
                    SEMAPHORE_RETRY_DELAY, workflowId);
            safeReenqueue(workflowId, SEMAPHORE_RETRY_DELAY);
        } catch (RuntimeException e) {
            // Runtime 계열은 Reconcile(P1-G) 복구 대상 — 루프는 살린다. Error 계열은 전파.
            log.error("Poller 처리 중 예외 — workflowId: {}", workflowId, e);
        }
    }

    private void safeReenqueue(Long workflowId, Duration delay) {
        try {
            pollQueue.offer(workflowId, delay);
        } catch (RuntimeException ex) {
            log.warn("재enqueue 실패 — Reconcile 이 복구 예정. workflowId: {}", workflowId, ex);
        }
    }
}
