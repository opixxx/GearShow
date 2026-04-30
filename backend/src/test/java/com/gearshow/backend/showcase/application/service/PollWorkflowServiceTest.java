package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.event.TripoSuccessEvent;
import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;
import com.gearshow.backend.showcase.application.port.in.PollWorkflowUseCase.PollOutcome;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationStatus;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoSemaphorePort;
import com.gearshow.backend.showcase.infrastructure.config.TripoApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link PollWorkflowService} 단위 테스트.
 *
 * <p>검증 매트릭스:</p>
 * <ul>
 *   <li>Skip: 워크플로우 없음 / GENERATING 아님 / 이미 tripoSucceededAt / taskId 공백</li>
 *   <li>IN_PROGRESS: {@code markPolled} 호출, affected=0 이면 SKIPPED</li>
 *   <li>SUCCESS: {@code markTripoSucceeded} affected=1 일 때만 이벤트 발행,
 *       affected=0 은 SKIPPED (중복 방지)</li>
 *   <li>FAILED: {@code markFailed(TRIPO_TASK_FAILED, ..., "TRIPO_API")}</li>
 *   <li>Semaphore 타임아웃: {@link TripoSemaphoreTimeoutException} 그대로 전파</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PollWorkflowService")
class PollWorkflowServiceTest {

    private static final Long WORKFLOW_ID = 99L;
    private static final Long SHOWCASE_ID = 99_100L;
    private static final String TASK_ID = "tripo-task-99";

    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private ModelGenerationClient modelGenerationClient;
    @Mock
    private TripoSemaphorePort tripoSemaphorePort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PollWorkflowService service;

    @BeforeEach
    void setUp() {
        TripoApiProperties properties = new TripoApiProperties(
                2_000L, "detailed", 200_000, "align_image");
        service = new PollWorkflowService(
                workflowPort, modelGenerationClient,
                tripoSemaphorePort, eventPublisher, properties);
        // 기본: semaphore 획득 → action 즉시 실행
        given(tripoSemaphorePort.runWithPermit(any(Duration.class), any()))
                .willAnswer(inv -> {
                    Supplier<?> action = inv.getArgument(1);
                    return action.get();
                });
    }

    private WorkflowSnapshot generatingSnapshot(String taskId, Instant tripoSucceededAt) {
        return new WorkflowSnapshot(
                WORKFLOW_ID, SHOWCASE_ID, "it-key", 1,
                WorkflowStep.GENERATING, taskId, tripoSucceededAt,
                Instant.now(), Instant.now(), null, null);
    }

    @Nested
    @DisplayName("Skip 경로")
    class SkipPaths {

        @Test
        @DisplayName("워크플로우 없음 → SKIPPED, Tripo 호출 없음")
        void noWorkflow_skips() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.empty());

            PollOutcome outcome = service.poll(WORKFLOW_ID);

            assertThat(outcome).isEqualTo(PollOutcome.SKIPPED);
            verify(modelGenerationClient, never()).fetchStatus(anyString());
        }

        @Test
        @DisplayName("currentStep != GENERATING → SKIPPED")
        void notGenerating_skips() {
            WorkflowSnapshot snap = new WorkflowSnapshot(
                    WORKFLOW_ID, SHOWCASE_ID, "k", 1,
                    WorkflowStep.PREPARING, null, null, Instant.now(), Instant.now(), null, null);
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(snap));

            assertThat(service.poll(WORKFLOW_ID)).isEqualTo(PollOutcome.SKIPPED);
            verify(modelGenerationClient, never()).fetchStatus(anyString());
        }

        @Test
        @DisplayName("이미 tripoSucceededAt 있음 → SKIPPED (중복 폴링 방지)")
        void alreadySucceeded_skips() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(TASK_ID, Instant.now())));

            assertThat(service.poll(WORKFLOW_ID)).isEqualTo(PollOutcome.SKIPPED);
            verify(modelGenerationClient, never()).fetchStatus(anyString());
        }

        @Test
        @DisplayName("tripo_task_id 공백 → SKIPPED (Reconcile 대기)")
        void blankTaskId_skips() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot("", null)));

            assertThat(service.poll(WORKFLOW_ID)).isEqualTo(PollOutcome.SKIPPED);
            verify(modelGenerationClient, never()).fetchStatus(anyString());
        }
    }

    @Nested
    @DisplayName("Tripo phase 분기")
    class TripoPhase {

        @BeforeEach
        void stubSnapshot() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(TASK_ID, null)));
        }

        @Test
        @DisplayName("IN_PROGRESS: markPolled 호출 + IN_PROGRESS 반환")
        void running_marksPolled() {
            given(modelGenerationClient.fetchStatus(TASK_ID)).willReturn(GenerationStatus.running());
            given(workflowPort.markPolled(WORKFLOW_ID)).willReturn(1);

            PollOutcome outcome = service.poll(WORKFLOW_ID);

            assertThat(outcome).isEqualTo(PollOutcome.IN_PROGRESS);
            verify(workflowPort, times(1)).markPolled(WORKFLOW_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("IN_PROGRESS 인데 markPolled affected=0 → SKIPPED")
        void running_polledZero_skips() {
            given(modelGenerationClient.fetchStatus(TASK_ID)).willReturn(GenerationStatus.running());
            given(workflowPort.markPolled(WORKFLOW_ID)).willReturn(0);

            assertThat(service.poll(WORKFLOW_ID)).isEqualTo(PollOutcome.SKIPPED);
        }

        @Test
        @DisplayName("SUCCESS: markTripoSucceeded affected=1 → TripoSuccessEvent 발행")
        void success_marksAndPublishes() {
            given(modelGenerationClient.fetchStatus(TASK_ID)).willReturn(GenerationStatus.success());
            given(workflowPort.markTripoSucceeded(WORKFLOW_ID)).willReturn(1);

            PollOutcome outcome = service.poll(WORKFLOW_ID);

            assertThat(outcome).isEqualTo(PollOutcome.SUCCEEDED);
            ArgumentCaptor<TripoSuccessEvent> captor = ArgumentCaptor.forClass(TripoSuccessEvent.class);
            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            assertThat(captor.getValue()).isEqualTo(new TripoSuccessEvent(WORKFLOW_ID, TASK_ID));
        }

        @Test
        @DisplayName("SUCCESS 인데 markTripoSucceeded affected=0 → 이벤트 미발행 (중복 방지)")
        void success_markZero_noEvent() {
            given(modelGenerationClient.fetchStatus(TASK_ID)).willReturn(GenerationStatus.success());
            given(workflowPort.markTripoSucceeded(WORKFLOW_ID)).willReturn(0);

            assertThat(service.poll(WORKFLOW_ID)).isEqualTo(PollOutcome.SKIPPED);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("FAILED: markFailed(TRIPO_TASK_FAILED, reason, TRIPO_API) 호출")
        void failed_marksFailed() {
            String reason = "invalid pose detected";
            given(modelGenerationClient.fetchStatus(TASK_ID))
                    .willReturn(GenerationStatus.failed(reason));
            given(workflowPort.markFailed(
                    eq(WORKFLOW_ID), eq(WorkflowFailureCode.TRIPO_TASK_FAILED),
                    anyString(), eq("TRIPO_API"))).willReturn(1);

            PollOutcome outcome = service.poll(WORKFLOW_ID);

            assertThat(outcome).isEqualTo(PollOutcome.FAILED);
            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID), eq(WorkflowFailureCode.TRIPO_TASK_FAILED),
                    anyString(), eq("TRIPO_API"));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("Semaphore 획득 실패")
    class SemaphoreTimeout {

        @Test
        @DisplayName("TripoSemaphoreTimeoutException 은 그대로 상위로 전파된다")
        void timeout_rethrows() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(TASK_ID, null)));
            given(tripoSemaphorePort.runWithPermit(any(Duration.class), any()))
                    .willThrow(new TripoSemaphoreTimeoutException());

            assertThatThrownBy(() -> service.poll(WORKFLOW_ID))
                    .isInstanceOf(TripoSemaphoreTimeoutException.class);

            verify(workflowPort, never()).markPolled(anyLong());
            verify(workflowPort, never()).markTripoSucceeded(anyLong());
            verify(workflowPort, never())
                    .markFailed(anyLong(), any(), anyString(), anyString());
        }
    }

    @SuppressWarnings("unused")
    private static ApplicationEventPublisher mockPublisher() {
        return mock(ApplicationEventPublisher.class);
    }
}
