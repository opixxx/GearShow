package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AdmissionQueueDrainScheduler} 단위 테스트 (ADR-025).
 *
 * <p>검증: cap 포화 시 미동작 / pollOldest→REQUESTED 검증 후 재투입 / REQUESTED 아니면 skip /
 * tick 당 상한(cap*2) / 큐 빔 break.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdmissionQueueDrainScheduler")
class AdmissionQueueDrainSchedulerTest {

    private static final int CAP = 2;

    @Mock
    private TripoAdmissionQueuePort admissionQueuePort;
    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private PrepareWorkflowUseCase prepareWorkflowUseCase;

    private AdmissionQueueDrainScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AdmissionQueueDrainScheduler(
                admissionQueuePort, workflowPort, prepareWorkflowUseCase,
                new AdmissionQueueProperties(CAP, true));
    }

    private WorkflowSnapshot snapshot(long id, WorkflowStep step) {
        return new WorkflowSnapshot(id, 1_000L, "k", 1, step,
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("countActive >= cap → pollOldest/prepare 모두 호출 안 함")
    void atCap_doesNothing() {
        given(workflowPort.countActive()).willReturn((long) CAP);

        scheduler.drain();

        verify(admissionQueuePort, never()).pollOldest();
        verify(prepareWorkflowUseCase, never()).prepare(anyLong());
    }

    @Test
    @DisplayName("slot 여유 + REQUESTED → prepare 재투입, 큐 빌 때까지")
    void underCap_requested_dispatches() {
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest())
                .willReturn(Optional.of(11L))
                .willReturn(Optional.of(12L))
                .willReturn(Optional.empty());
        given(workflowPort.findSnapshot(11L)).willReturn(Optional.of(snapshot(11L, WorkflowStep.REQUESTED)));
        given(workflowPort.findSnapshot(12L)).willReturn(Optional.of(snapshot(12L, WorkflowStep.REQUESTED)));

        scheduler.drain();

        verify(prepareWorkflowUseCase, times(1)).prepare(11L);
        verify(prepareWorkflowUseCase, times(1)).prepare(12L);
    }

    @Test
    @DisplayName("pop 했으나 REQUESTED 아님 → prepare 호출 안 함 (skip)")
    void poppedButNotRequested_skipsPrepare() {
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest())
                .willReturn(Optional.of(21L))
                .willReturn(Optional.empty());
        given(workflowPort.findSnapshot(21L))
                .willReturn(Optional.of(snapshot(21L, WorkflowStep.GENERATING)));

        scheduler.drain();

        verify(prepareWorkflowUseCase, never()).prepare(anyLong());
    }

    @Test
    @DisplayName("tick 당 상한 cap*2 — 무한 공급이어도 그 이상 dispatch 하지 않는다")
    void tickLimit_capped() {
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest()).willReturn(Optional.of(99L));  // 무한 공급
        given(workflowPort.findSnapshot(99L))
                .willReturn(Optional.of(snapshot(99L, WorkflowStep.REQUESTED)));

        scheduler.drain();

        // limit = cap*2 = 4
        verify(admissionQueuePort, times(CAP * 2)).pollOldest();
        verify(prepareWorkflowUseCase, times(CAP * 2)).prepare(99L);
    }

    @Test
    @DisplayName("큐가 비어있으면 즉시 break")
    void emptyQueue_breaks() {
        given(workflowPort.countActive()).willReturn(0L);
        given(admissionQueuePort.pollOldest()).willReturn(Optional.empty());

        scheduler.drain();

        verify(prepareWorkflowUseCase, never()).prepare(anyLong());
    }
}
