package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.exception.SemaphoreInterruptedException;
import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;
import com.gearshow.backend.showcase.application.port.in.PollWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.in.PollWorkflowUseCase.PollOutcome;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.WorkflowPollingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link WorkflowPollingScheduler} 단위 테스트.
 *
 * <p>블로킹 루프 자체를 직접 돌리지 않고 {@code handle} 분기(각 예외/결과 케이스) 만 검증한다.
 * {@code startLoop} 는 다음 패턴으로 간접 검증 — {@code take()} 1회 성공 → 2회째
 * {@code InterruptedException} 을 던져 루프 종료시킨다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowPollingScheduler")
class WorkflowPollingSchedulerTest {

    private static final Long WORKFLOW_ID = 123L;
    private static final Duration DEFAULT_DELAY = Duration.ofSeconds(30);
    private static final Duration SEMAPHORE_RETRY = Duration.ofSeconds(5);

    @Mock
    private WorkflowPollQueuePort pollQueue;
    @Mock
    private PollWorkflowUseCase pollUseCase;

    private WorkflowPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        // delaySeconds=30 으로 고정 — 프로덕션 기본값과 동일
        WorkflowPollingProperties properties = new WorkflowPollingProperties(30, true);
        scheduler = new WorkflowPollingScheduler(pollQueue, pollUseCase, properties);
    }

    @Nested
    @DisplayName("startLoop")
    class StartLoop {

        @Test
        @DisplayName("take() 가 InterruptedException 을 던지면 루프가 종료되고 인터럽트 플래그를 복원한다")
        void takeInterrupted_returnsAndSetsInterruptFlag() throws Exception {
            given(pollQueue.take()).willThrow(new InterruptedException("shutdown"));

            try {
                scheduler.startLoop();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                // 다른 테스트에 영향 없도록 인터럽트 플래그 정리
                Thread.interrupted();
            }

            verify(pollUseCase, never()).poll(anyLong());
        }

        @Test
        @DisplayName("take() 1회 성공 후 2회째 InterruptedException 으로 루프 종료 — handle 은 1회만 호출")
        void oneSuccessfulTakeThenInterrupt() throws Exception {
            given(pollQueue.take())
                    .willReturn(WORKFLOW_ID)
                    .willThrow(new InterruptedException("shutdown"));
            given(pollUseCase.poll(WORKFLOW_ID)).willReturn(PollOutcome.SUCCEEDED);

            try {
                scheduler.startLoop();
            } finally {
                Thread.interrupted();
            }

            verify(pollUseCase, times(1)).poll(WORKFLOW_ID);
        }
    }

    @Nested
    @DisplayName("handle 분기 — startLoop 내부 호출을 통해 간접 검증")
    class HandleBranch {

        private void stubOneCycleThenStop() throws InterruptedException {
            given(pollQueue.take())
                    .willReturn(WORKFLOW_ID)
                    .willThrow(new InterruptedException("stop"));
        }

        private void runAndClearInterrupt() {
            try {
                scheduler.startLoop();
            } finally {
                Thread.interrupted();
            }
        }

        @Test
        @DisplayName("IN_PROGRESS — defaultDelay 로 재enqueue")
        void inProgress_reEnqueuesWithDefaultDelay() throws Exception {
            stubOneCycleThenStop();
            given(pollUseCase.poll(WORKFLOW_ID)).willReturn(PollOutcome.IN_PROGRESS);

            runAndClearInterrupt();

            verify(pollQueue, times(1)).offer(WORKFLOW_ID, DEFAULT_DELAY);
        }

        @Test
        @DisplayName("SUCCEEDED — 재enqueue 없음")
        void succeeded_noReenqueue() throws Exception {
            stubOneCycleThenStop();
            given(pollUseCase.poll(WORKFLOW_ID)).willReturn(PollOutcome.SUCCEEDED);

            runAndClearInterrupt();

            verify(pollQueue, never()).offer(anyLong(), any());
        }

        @Test
        @DisplayName("SKIPPED — 재enqueue 없음")
        void skipped_noReenqueue() throws Exception {
            stubOneCycleThenStop();
            given(pollUseCase.poll(WORKFLOW_ID)).willReturn(PollOutcome.SKIPPED);

            runAndClearInterrupt();

            verify(pollQueue, never()).offer(anyLong(), any());
        }

        @Test
        @DisplayName("FAILED — 재enqueue 없음")
        void failed_noReenqueue() throws Exception {
            stubOneCycleThenStop();
            given(pollUseCase.poll(WORKFLOW_ID)).willReturn(PollOutcome.FAILED);

            runAndClearInterrupt();

            verify(pollQueue, never()).offer(anyLong(), any());
        }

        @Test
        @DisplayName("TripoSemaphoreTimeoutException — 5s 재enqueue 후 루프 계속")
        void semaphoreTimeout_reEnqueuesShort() throws Exception {
            stubOneCycleThenStop();
            given(pollUseCase.poll(WORKFLOW_ID)).willThrow(new TripoSemaphoreTimeoutException());

            runAndClearInterrupt();

            verify(pollQueue, times(1)).offer(WORKFLOW_ID, SEMAPHORE_RETRY);
        }

        @Test
        @DisplayName("SemaphoreInterruptedException — delay 0 재enqueue 후 루프 종료 + 인터럽트 플래그")
        void semaphoreInterrupted_reenqueuesImmediatelyAndTerminates() throws Exception {
            given(pollQueue.take())
                    .willReturn(WORKFLOW_ID)
                    .willReturn(999L);   // 만약 루프가 종료 안 되면 실패 케이스
            given(pollUseCase.poll(WORKFLOW_ID)).willThrow(new SemaphoreInterruptedException());

            try {
                scheduler.startLoop();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }

            verify(pollQueue, times(1)).offer(WORKFLOW_ID, Duration.ZERO);
            verify(pollUseCase, times(1)).poll(WORKFLOW_ID);
            // 두 번째 workflowId 는 루프 종료로 인해 poll 되지 않아야 한다
            verify(pollUseCase, never()).poll(999L);
        }

        @Test
        @DisplayName("일반 RuntimeException — 로그만, 재enqueue 없음, 루프 계속")
        void runtimeException_swallowedLoopContinues() throws Exception {
            // 세 번 take (두 workflowId + 인터럽트). poll 이 첫 건에 예외를 던져도 루프는 살아 두 번째를 처리해야 한다.
            given(pollQueue.take())
                    .willReturn(WORKFLOW_ID)
                    .willReturn(456L)
                    .willThrow(new InterruptedException("stop"));
            given(pollUseCase.poll(WORKFLOW_ID))
                    .willThrow(new RuntimeException("DB 일시 오류"));
            given(pollUseCase.poll(456L)).willReturn(PollOutcome.SUCCEEDED);

            runAndClearInterrupt();

            verify(pollUseCase, times(1)).poll(WORKFLOW_ID);
            verify(pollUseCase, times(1)).poll(456L);
            verify(pollQueue, never()).offer(eq(WORKFLOW_ID), any());
        }

        @Test
        @DisplayName("safeReenqueue — offer 도중 RuntimeException 발생해도 루프는 계속된다")
        void safeReenqueueSwallowsOfferException() throws Exception {
            given(pollQueue.take())
                    .willReturn(WORKFLOW_ID)
                    .willReturn(456L)
                    .willThrow(new InterruptedException("stop"));
            given(pollUseCase.poll(WORKFLOW_ID)).willThrow(new TripoSemaphoreTimeoutException());
            // offer 가 실패 — safeReenqueue 내부에서 삼켜야 한다
            org.mockito.Mockito.doThrow(new RuntimeException("Redis offer 실패"))
                    .when(pollQueue).offer(eq(WORKFLOW_ID), eq(SEMAPHORE_RETRY));
            given(pollUseCase.poll(456L)).willReturn(PollOutcome.SUCCEEDED);

            runAndClearInterrupt();

            verify(pollUseCase, times(1)).poll(456L);
        }
    }
}
