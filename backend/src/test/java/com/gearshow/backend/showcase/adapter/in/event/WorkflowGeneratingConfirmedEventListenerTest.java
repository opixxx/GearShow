package com.gearshow.backend.showcase.adapter.in.event;

import com.gearshow.backend.showcase.application.event.WorkflowGeneratingConfirmedEvent;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.TripoPollingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link WorkflowGeneratingConfirmedEventListener} 단위 테스트.
 *
 * <p>검증 매트릭스: Redis 활성/비활성 / offer 성공 / offer 예외 삼킴.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowGeneratingConfirmedEventListener")
class WorkflowGeneratingConfirmedEventListenerTest {

    private static final Long WORKFLOW_ID = 88L;

    @Mock
    private WorkflowPollQueuePort pollQueue;

    private TripoPollingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new TripoPollingProperties(30, 2_000L, true);
    }

    @Test
    @DisplayName("Redis 활성 + offer 성공 → pollQueue.offer 호출")
    void redisEnabled_happyOffer() {
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowPollQueuePort> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(pollQueue);
        WorkflowGeneratingConfirmedEventListener listener =
                new WorkflowGeneratingConfirmedEventListener(provider, properties);

        listener.onConfirmed(new WorkflowGeneratingConfirmedEvent(WORKFLOW_ID));

        verify(pollQueue, times(1)).offer(WORKFLOW_ID, Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("Redis 비활성 (Bean 없음) → 조용히 no-op")
    void redisDisabled_noOp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowPollQueuePort> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(null);
        WorkflowGeneratingConfirmedEventListener listener =
                new WorkflowGeneratingConfirmedEventListener(provider, properties);

        listener.onConfirmed(new WorkflowGeneratingConfirmedEvent(WORKFLOW_ID));

        verify(pollQueue, never()).offer(anyLong(), any());
    }

    @Test
    @DisplayName("offer 도중 RuntimeException → 예외 삼킴 (Reconcile 복구)")
    void offerThrows_swallowed() {
        @SuppressWarnings("unchecked")
        ObjectProvider<WorkflowPollQueuePort> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(pollQueue);
        doThrow(new RuntimeException("Redis 일시 오류"))
                .when(pollQueue).offer(anyLong(), any());
        WorkflowGeneratingConfirmedEventListener listener =
                new WorkflowGeneratingConfirmedEventListener(provider, properties);

        listener.onConfirmed(new WorkflowGeneratingConfirmedEvent(WORKFLOW_ID));  // throw 금지

        verify(pollQueue, times(1)).offer(anyLong(), any());
    }
}
