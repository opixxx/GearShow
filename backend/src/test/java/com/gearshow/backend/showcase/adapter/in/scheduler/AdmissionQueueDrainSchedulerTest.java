package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.out.AdmissionDrainLockPort;
import com.gearshow.backend.showcase.application.port.out.AdmissionMetricsPort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import com.gearshow.backend.showcase.infrastructure.config.ShowcaseKafkaTopicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AdmissionQueueDrainScheduler} 단위 테스트 (ADR-025 재발행 드레이너).
 *
 * <p>검증: drain-lock 미획득 skip / available≤0 무송신 / REQUESTED pop→ofDrain 재발행+
 * dispatched / REQUESTED 아님 skip / tick 당 available 상한 / lock 항상 unlock /
 * 드레이너가 prepare 류를 직접 호출하지 않음(생성자에 PrepareWorkflowUseCase 부재).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdmissionQueueDrainScheduler (재발행)")
class AdmissionQueueDrainSchedulerTest {

    private static final String TOPIC = ShowcaseKafkaTopicConfig.MODEL_GENERATION_REQUEST_TOPIC;

    @Mock
    private AdmissionDrainLockPort drainLockPort;
    @Mock
    private TripoAdmissionQueuePort admissionQueuePort;
    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private AdmissionMetricsPort metricsPort;
    @Mock
    private KafkaTemplate<String, ModelGenerationRequestMessage> kafkaTemplate;

    private AdmissionQueueDrainScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = schedulerWith(new AdmissionQueueProperties(10, true));
        CompletableFuture<SendResult<String, ModelGenerationRequestMessage>> ok =
                CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(ok);
    }

    private AdmissionQueueDrainScheduler schedulerWith(AdmissionQueueProperties props) {
        return new AdmissionQueueDrainScheduler(
                drainLockPort, admissionQueuePort, workflowPort, props, metricsPort, kafkaTemplate);
    }

    private WorkflowSnapshot snapshot(long id, long showcaseId, WorkflowStep step) {
        return new WorkflowSnapshot(id, showcaseId, "it-key", 1, step,
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("drain-lock 미획득: countActive/pop/send 전부 없음")
    void lockNotAcquired_doesNothing() {
        given(drainLockPort.tryLock(any())).willReturn(false);

        scheduler.drain();

        verify(workflowPort, never()).countActive();
        verify(admissionQueuePort, never()).pollOldest();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(drainLockPort, never()).unlock();
    }

    @Test
    @DisplayName("available<=0(cap 도달): pop/send 없음, lock 은 unlock")
    void noAvailableSlot_unlocksAndStops() {
        given(drainLockPort.tryLock(any())).willReturn(true);
        given(workflowPort.countActive()).willReturn(10L);

        scheduler.drain();

        verify(admissionQueuePort, never()).pollOldest();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(drainLockPort, times(1)).unlock();
    }

    @Test
    @DisplayName("REQUESTED pop: ofDrain(bypass=true) 재발행 + dispatched(1) + unlock")
    void requestedPopped_republishesBypassMessage() {
        given(drainLockPort.tryLock(any())).willReturn(true);
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest())
                .willReturn(Optional.of(1L))
                .willReturn(Optional.empty());
        given(workflowPort.findSnapshot(1L))
                .willReturn(Optional.of(snapshot(1L, 100L, WorkflowStep.REQUESTED)));

        scheduler.drain();

        verify(kafkaTemplate, times(1)).send(eq(TOPIC), eq("100"),
                argThat(m -> m.bypassAdmission()
                        && m.workflowId().equals(1L)
                        && m.showcaseId().equals(100L)
                        && m.messageId().startsWith("model-generation:drain:1:")));
        verify(metricsPort, times(1)).dispatched(1);
        verify(drainLockPort, times(1)).unlock();
    }

    @Test
    @DisplayName("pop 했으나 REQUESTED 아님: 재발행 skip")
    void poppedButNotRequested_skips() {
        given(drainLockPort.tryLock(any())).willReturn(true);
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest())
                .willReturn(Optional.of(2L))
                .willReturn(Optional.empty());
        given(workflowPort.findSnapshot(2L))
                .willReturn(Optional.of(snapshot(2L, 200L, WorkflowStep.GENERATING)));

        scheduler.drain();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(metricsPort, never()).dispatched(anyInt());
        verify(drainLockPort, times(1)).unlock();
    }

    @Test
    @DisplayName("tick 당 available 상한: cap2/active0 → ZSET 5건이어도 2건만 재발행")
    void tickCap_limitsToAvailable() {
        scheduler = schedulerWith(new AdmissionQueueProperties(2, true));
        given(drainLockPort.tryLock(any())).willReturn(true);
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest())
                .willReturn(Optional.of(1L))
                .willReturn(Optional.of(2L))
                .willReturn(Optional.of(3L));
        given(workflowPort.findSnapshot(anyLong())).willAnswer(inv ->
                Optional.of(snapshot(inv.getArgument(0), 900L, WorkflowStep.REQUESTED)));

        scheduler.drain();

        verify(admissionQueuePort, times(2)).pollOldest();
        verify(kafkaTemplate, times(2)).send(anyString(), anyString(), any());
        verify(metricsPort, times(1)).dispatched(2);
    }

    @Test
    @DisplayName("send 실패: republishFailed 카운터(예외 흡수, 드레이너는 계속)")
    void sendFailure_incrementsRepublishFailed() {
        given(drainLockPort.tryLock(any())).willReturn(true);
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest())
                .willReturn(Optional.of(7L))
                .willReturn(Optional.empty());
        given(workflowPort.findSnapshot(7L))
                .willReturn(Optional.of(snapshot(7L, 700L, WorkflowStep.REQUESTED)));
        CompletableFuture<SendResult<String, ModelGenerationRequestMessage>> failed =
                CompletableFuture.failedFuture(new RuntimeException("send fail"));
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(failed);

        scheduler.drain();

        verify(metricsPort, times(1)).republishFailed();
        verify(drainLockPort, times(1)).unlock();
    }
}
