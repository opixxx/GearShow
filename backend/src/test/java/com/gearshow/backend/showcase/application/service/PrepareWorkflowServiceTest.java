package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.event.WorkflowGeneratingConfirmedEvent;
import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;
import com.gearshow.backend.showcase.application.exception.ModelGenerationRetryableException;
import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;
import com.gearshow.backend.showcase.application.port.out.AdmissionMetricsPort;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link PrepareWorkflowService} 단위 테스트 (TX1 + Tripo + TX2 전 구간).
 *
 * <p>검증 매트릭스:</p>
 * <ul>
 *   <li>Skip: workflow 없음, TX1 affected=0, 락 busy</li>
 *   <li>검증 실패: 소스 이미지 < 4, S3 missing</li>
 *   <li>Tripo: Happy / Retryable throw / NonRetryable markFailed / CircuitOpen throw</li>
 *   <li>Tripo 후: pending INSERT 실패 throw, TX2 affected=0 skip, TX2 DB 실패 swallow</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PrepareWorkflowService")
class PrepareWorkflowServiceTest {

    private static final Long WORKFLOW_ID = 77L;
    private static final Long SHOWCASE_ID = 77_100L;
    private static final String TRIPO_TASK_ID = "tripo-task-xyz";
    private static final List<String> FOUR_URLS =
            List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg");

    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private ModelSourceImagePort modelSourceImagePort;
    @Mock
    private ImageStoragePort imageStoragePort;
    @Mock
    private WorkflowLockPort workflowLockPort;
    @Mock
    private ModelGenerationClient modelGenerationClient;
    @Mock
    private TripoPendingTaskPort tripoPendingTaskPort;
    @Mock
    private PrepareWorkflowTxHelper txHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TripoAdmissionQueuePort admissionQueuePort;
    @Mock
    private AdmissionMetricsPort admissionMetricsPort;

    private static final AdmissionQueueProperties ADMISSION_DEFAULT =
            new AdmissionQueueProperties(10, true);

    private PrepareWorkflowService service;

    private PrepareWorkflowService serviceWith(AdmissionQueueProperties props) {
        return new PrepareWorkflowService(
                workflowPort, modelSourceImagePort, imageStoragePort,
                workflowLockPort, modelGenerationClient, tripoPendingTaskPort,
                txHelper, eventPublisher,
                admissionQueuePort, props, admissionMetricsPort);
    }

    @BeforeEach
    void setUp() {
        service = serviceWith(ADMISSION_DEFAULT);
        // 기본: 락 획득 성공 → action 즉시 실행
        doAnswer(invocation -> {
            WorkflowLockPort.LockedAction action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(workflowLockPort).withLock(anyLong(), any());
    }

    private WorkflowSnapshot requestedSnapshot() {
        return new WorkflowSnapshot(
                WORKFLOW_ID, SHOWCASE_ID, "it-key", 1,
                WorkflowStep.REQUESTED, null, null, null, null, null, null);
    }

    private void stubThroughImagesValid() {
        given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
        given(workflowPort.updateStepIfCurrent(
                WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING)).willReturn(1);
        given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
        given(imageStoragePort.existsByUrl(anyString())).willReturn(true);
    }

    @Nested
    @DisplayName("Skip 경로")
    class SkipPaths {

        @Test
        @DisplayName("워크플로우가 없으면 조건부 UPDATE 조차 호출하지 않는다")
        void noWorkflow_doesNothing() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.empty());

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, never()).updateStepIfCurrent(anyLong(), any(), any());
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }

        @Test
        @DisplayName("REQUESTED→PREPARING 전환이 affected=0 이면 Tripo 호출 없이 skip 한다")
        void alreadyTransitioned_skipsTripo() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            // 검증은 락 잡기 전 호출되므로 통과시킨다
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
            given(imageStoragePort.existsByUrl(anyString())).willReturn(true);
            given(workflowPort.updateStepIfCurrent(WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING))
                    .willReturn(0);

            service.prepare(WORKFLOW_ID);

            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }

        @Test
        @DisplayName("TX1 분산 락 busy → Tripo 호출 안 함")
        void lockBusy_skipsTripo() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            // 검증은 락 잡기 전 호출되므로 통과시킨다
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
            given(imageStoragePort.existsByUrl(anyString())).willReturn(true);
            doThrow(new WorkflowLockBusyException(WORKFLOW_ID))
                    .when(workflowLockPort).withLock(eq(WORKFLOW_ID), any());

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, never()).updateStepIfCurrent(anyLong(), any(), any());
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }

        @Test
        @DisplayName("검증 실패 시 락을 획득하지 않고 markFailed 후 종료한다")
        void invalidSourceImages_neverAcquiresLock() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            // 이미지 4장 미달 — 검증 실패 분기
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID))
                    .willReturn(List.of("only-one.jpg"));
            given(workflowPort.markFailed(
                    eq(WORKFLOW_ID), eq(WorkflowFailureCode.SOURCE_IMAGES_MISSING),
                    anyString(), anyString())).willReturn(1);

            service.prepare(WORKFLOW_ID);

            // 락 진입 없음 — 영구 실패 검증은 락 밖에서 끝나야 한다
            verify(workflowLockPort, never()).withLock(anyLong(), any());
            verify(workflowPort, never()).updateStepIfCurrent(anyLong(), any(), any());
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID), eq(WorkflowFailureCode.SOURCE_IMAGES_MISSING),
                    anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("소스 이미지 검증")
    class SourceImageValidation {

        @BeforeEach
        void stubTransitionSuccess() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            given(workflowPort.updateStepIfCurrent(
                    WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING)).willReturn(1);
        }

        @Test
        @DisplayName("소스 이미지가 4장 미만이면 SOURCE_IMAGES_MISSING 로 FAILED 마킹하고 Tripo 호출 안 함")
        void fewerThanFour_marksFailedAndNoTripo() {
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID))
                    .willReturn(List.of("a.jpg", "b.jpg", "c.jpg"));

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.SOURCE_IMAGES_MISSING),
                    anyString(),
                    eq("INTERNAL"));
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }

        @Test
        @DisplayName("S3 객체 1개라도 누락되면 S3_KEY_MISSING 으로 FAILED 마킹")
        void missingS3Object_marksFailedAndStops() {
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
            given(imageStoragePort.existsByUrl("a.jpg")).willReturn(true);
            given(imageStoragePort.existsByUrl("b.jpg")).willReturn(false);

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.S3_KEY_MISSING),
                    anyString(),
                    eq("S3"));
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }
    }

    @Nested
    @DisplayName("Tripo 호출")
    class TripoCall {

        @Test
        @DisplayName("Happy: Tripo 성공 → pending 선저장 → TX2(executeTx2) 성공 → Poller 재queue")
        void happyPath_transitionsToGenerating() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willReturn(TRIPO_TASK_ID);
            given(txHelper.executeTx2(WORKFLOW_ID, TRIPO_TASK_ID)).willReturn(1);

            service.prepare(WORKFLOW_ID);

            verify(tripoPendingTaskPort, times(1)).preserve(WORKFLOW_ID, TRIPO_TASK_ID);
            verify(txHelper, times(1)).executeTx2(WORKFLOW_ID, TRIPO_TASK_ID);
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(eventPublisher, times(1))
                    .publishEvent(new WorkflowGeneratingConfirmedEvent(WORKFLOW_ID));
        }

        @Test
        @DisplayName("Retryable + 보상 affected=1: PREPARING→REQUESTED 보상 후 예외 rethrow (ADR-026)")
        void retryable_compensatesAndRethrows() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willThrow(new ModelGenerationRetryableException(ErrorCode.TRIPO_RATE_LIMITED));
            given(workflowPort.compensatePreparingToRequested(WORKFLOW_ID)).willReturn(1);

            assertThatThrownBy(() -> service.prepare(WORKFLOW_ID))
                    .isInstanceOf(ModelGenerationRetryableException.class);

            // C1 불변식: 보상은 반드시 PREPARING 전이(TX1) *후* 호출돼야 한다.
            // 누군가 보상을 transitionToPreparing 앞으로 옮기는 회귀를 InOrder 로 차단.
            InOrder order = inOrder(workflowPort);
            order.verify(workflowPort).updateStepIfCurrent(
                    WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);
            order.verify(workflowPort).compensatePreparingToRequested(WORKFLOW_ID);
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(tripoPendingTaskPort, never()).preserve(anyLong(), anyString());
        }

        @Test
        @DisplayName("Retryable + 보상 affected=0(과금됨/이미 전이): rethrow 안 함 — 후속 단계 미진입")
        void retryable_compensateZero_doesNotRethrow() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willThrow(new ModelGenerationRetryableException(ErrorCode.TRIPO_RATE_LIMITED));
            given(workflowPort.compensatePreparingToRequested(WORKFLOW_ID)).willReturn(0);

            service.prepare(WORKFLOW_ID);  // throw 금지

            verify(workflowPort, times(1)).compensatePreparingToRequested(WORKFLOW_ID);
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(tripoPendingTaskPort, never()).preserve(anyLong(), anyString());
            // 보상 affected=0 → 호출자 정상 종료(후속 단계 미진입)
            verify(txHelper, never()).executeTx2(anyLong(), anyString());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("TripoSemaphoreTimeout(=Retryable 하위): 보상 후 rethrow")
        void semaphoreTimeout_compensatesAndRethrows() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willThrow(new TripoSemaphoreTimeoutException());
            given(workflowPort.compensatePreparingToRequested(WORKFLOW_ID)).willReturn(1);

            assertThatThrownBy(() -> service.prepare(WORKFLOW_ID))
                    .isInstanceOf(TripoSemaphoreTimeoutException.class);

            verify(workflowPort, times(1)).compensatePreparingToRequested(WORKFLOW_ID);
            verify(tripoPendingTaskPort, never()).preserve(anyLong(), anyString());
        }

        @Test
        @DisplayName("NonRetryable: TRIPO_NON_RETRYABLE 로 FAILED 마킹 + alertRequired 경로")
        void nonRetryable_marksFailed() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willThrow(new ModelGenerationNonRetryableException(
                            ErrorCode.TRIPO_INSUFFICIENT_CREDIT, true));

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.TRIPO_NON_RETRYABLE),
                    anyString(),
                    eq("TRIPO_API"));
            // 회귀 방지: NonRetryable 은 보상 대상 아님 (ADR-026 — 미과금 확정 retryable 만)
            verify(workflowPort, never()).compensatePreparingToRequested(anyLong());
            verify(tripoPendingTaskPort, never()).preserve(anyLong(), anyString());
        }

        @Test
        @DisplayName("CircuitBreaker OPEN + 보상 affected=1: 보상 후 CallNotPermittedException rethrow")
        void circuitOpen_compensatesAndRethrows() {
            stubThroughImagesValid();
            CircuitBreaker cb = CircuitBreaker.of("test",
                    CircuitBreakerConfig.custom().build());
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willThrow(CallNotPermittedException.createCallNotPermittedException(cb));
            given(workflowPort.compensatePreparingToRequested(WORKFLOW_ID)).willReturn(1);

            assertThatThrownBy(() -> service.prepare(WORKFLOW_ID))
                    .isInstanceOf(CallNotPermittedException.class);

            verify(workflowPort, times(1)).compensatePreparingToRequested(WORKFLOW_ID);
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(tripoPendingTaskPort, never()).preserve(anyLong(), anyString());
        }

        @Test
        @DisplayName("pending INSERT 실패: 예외 rethrow — task_id 유실 방지 위해 재시도 유도")
        void pendingInsertFails_rethrows() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willReturn(TRIPO_TASK_ID);
            doThrow(new DataAccessResourceFailureException("DB 연결 실패"))
                    .when(tripoPendingTaskPort).preserve(WORKFLOW_ID, TRIPO_TASK_ID);

            assertThatThrownBy(() -> service.prepare(WORKFLOW_ID))
                    .isInstanceOf(DataAccessResourceFailureException.class);

            verify(txHelper, never()).executeTx2(anyLong(), anyString());
        }

        @Test
        @DisplayName("TX2 affected=0: pending 유지 (helper 가 내부에서 미정리), Poller offer 도 호출 안 함")
        void tx2Zero_keepsPendingAndReturns() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willReturn(TRIPO_TASK_ID);
            given(txHelper.executeTx2(WORKFLOW_ID, TRIPO_TASK_ID)).willReturn(0);

            service.prepare(WORKFLOW_ID);

            verify(tripoPendingTaskPort, times(1)).preserve(WORKFLOW_ID, TRIPO_TASK_ID);
            verify(txHelper, times(1)).executeTx2(WORKFLOW_ID, TRIPO_TASK_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("TX2 DB 실패: 예외 삼킴, WorkflowGeneratingConfirmedEvent 도 발행 안 함")
        void tx2DbFail_swallowsException() {
            stubThroughImagesValid();
            given(modelGenerationClient.startGeneration(WORKFLOW_ID, SHOWCASE_ID))
                    .willReturn(TRIPO_TASK_ID);
            given(txHelper.executeTx2(WORKFLOW_ID, TRIPO_TASK_ID))
                    .willThrow(new DataAccessResourceFailureException("TX2 DB 실패"));

            service.prepare(WORKFLOW_ID);  // throw 금지

            verify(tripoPendingTaskPort, times(1)).preserve(WORKFLOW_ID, TRIPO_TASK_ID);
            verify(txHelper, times(1)).executeTx2(WORKFLOW_ID, TRIPO_TASK_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("입장 게이트 (ADR-025)")
    class AdmissionGate {

        @Test
        @DisplayName("active < cap: 게이트 통과 → 정상 전이, park 안 함")
        void underCap_passes() {
            stubThroughImagesValid();
            given(workflowPort.countActive()).willReturn(9L);

            service.prepare(WORKFLOW_ID);

            verify(admissionQueuePort, never()).parkIfAbsent(anyLong(), anyLong());
            verify(admissionMetricsPort, never()).parkOccurred();
            verify(workflowPort, times(1))
                    .updateStepIfCurrent(WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);
        }

        @Test
        @DisplayName("active >= cap: tripo:queue park + parkOccurred, 전이/Tripo 호출 안 함")
        void atCap_parksAndStops() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
            given(imageStoragePort.existsByUrl(anyString())).willReturn(true);
            given(workflowPort.countActive()).willReturn(10L);

            service.prepare(WORKFLOW_ID);

            verify(admissionQueuePort, times(1)).parkIfAbsent(eq(WORKFLOW_ID), anyLong());
            verify(admissionMetricsPort, times(1)).parkOccurred();
            verify(workflowPort, never()).updateStepIfCurrent(anyLong(), any(), any());
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }

        @Test
        @DisplayName("enabled=false(롤백): cap 도달이어도 게이트 항상 통과")
        void disabled_alwaysPasses() {
            PrepareWorkflowService disabled = serviceWith(new AdmissionQueueProperties(10, false));
            stubThroughImagesValid();

            disabled.prepare(WORKFLOW_ID);

            verify(admissionQueuePort, never()).parkIfAbsent(anyLong(), anyLong());
            verify(workflowPort, never()).countActive();
            verify(workflowPort, times(1))
                    .updateStepIfCurrent(WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);
        }

        @Test
        @DisplayName("prepareFromAdmissionQueue: 게이트 미경유(countActive 조회조차 안 함) + recordInflight")
        void fromQueue_bypassesGateAndRecordsInflight() {
            stubThroughImagesValid();

            service.prepareFromAdmissionQueue(WORKFLOW_ID, 1_000L);

            verify(workflowPort, never()).countActive();
            verify(admissionQueuePort, never()).parkIfAbsent(anyLong(), anyLong());
            verify(workflowPort, times(1))
                    .updateStepIfCurrent(WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);
            verify(admissionMetricsPort, times(1)).recordInflight(1_000L);
        }

        @Test
        @DisplayName("prepareFromAdmissionQueue affected=0: bypassSkipped, recordInflight 안 함")
        void fromQueue_affectedZero_bypassSkipped() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
            given(imageStoragePort.existsByUrl(anyString())).willReturn(true);
            given(workflowPort.updateStepIfCurrent(
                    WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING)).willReturn(0);

            service.prepareFromAdmissionQueue(WORKFLOW_ID, 2_000L);

            verify(admissionMetricsPort, times(1)).bypassSkipped();
            verify(admissionMetricsPort, never()).recordInflight(anyLong());
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }

        @Test
        @DisplayName("이미 PREPARING(비-REQUESTED): 게이트 미진입·park 안 함·전이 안 함 (큐 오염 차단)")
        void notRequested_skipsBeforeGate() {
            WorkflowSnapshot preparing = new WorkflowSnapshot(
                    WORKFLOW_ID, SHOWCASE_ID, "it-key", 1, WorkflowStep.PREPARING,
                    null, null, null, null, null, null);
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(preparing));
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(FOUR_URLS);
            given(imageStoragePort.existsByUrl(anyString())).willReturn(true);

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, never()).countActive();
            verify(admissionQueuePort, never()).parkIfAbsent(anyLong(), anyLong());
            verify(admissionMetricsPort, never()).parkOccurred();
            verify(workflowPort, never()).updateStepIfCurrent(anyLong(), any(), any());
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
        }
    }
}
