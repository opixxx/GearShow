package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.StuckWorkflow;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.event.TripoSuccessEvent;
import com.gearshow.backend.showcase.application.event.WorkflowGeneratingConfirmedEvent;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.infrastructure.config.AdmissionQueueProperties;
import com.gearshow.backend.showcase.infrastructure.config.ReconcileProperties;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ReconcileStuckWorkflowsService} 단위 테스트 (설계 §8.4, ADR-012).
 *
 * <p>Redrive 전략 검증:</p>
 * <ul>
 *   <li>PREPARING stuck — tripo_pending_task 있으면 markGenerating 재개</li>
 *   <li>PREPARING stuck — tripo_pending_task 없으면 markFailed(TX2_DB_FAILED)</li>
 *   <li>락 busy → 안전하게 skip</li>
 *   <li>heartbeat 복구 상태 → skip (오판 방어)</li>
 *   <li>GENERATING·Tripo stuck → DelayedQueue 재등록</li>
 *   <li>GENERATING·S3 stuck → TripoSuccessEvent 재발행</li>
 *   <li>REQUESTED stuck → 경고 로그 + 입장 큐 미포함 시 재park (ADR-025) / 포함 시 skip</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReconcileStuckWorkflowsService")
class ReconcileStuckWorkflowsServiceTest {

    private static final Long WORKFLOW_ID = 501L;
    private static final Long SHOWCASE_ID = 501_100L;
    private static final String TASK_ID = "tripo-task-501";

    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private TripoPendingTaskPort tripoPendingTaskPort;
    @Mock
    private WorkflowLockPort workflowLockPort;
    @Mock
    private WorkflowPollQueuePort workflowPollQueuePort;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TripoAdmissionQueuePort admissionQueuePort;

    /** 기본: 입장 게이트 활성 (cap=10). enabled=false 롤백 케이스는 별도 서비스 인스턴스로 검증. */
    private final AdmissionQueueProperties admissionProperties =
            new AdmissionQueueProperties(10, true);

    private ReconcileStuckWorkflowsService service;

    @BeforeEach
    void setUp() {
        ReconcileProperties properties = new ReconcileProperties(
                true, 60_000L, 50, 60L, 8L, 5L, 30L, 5L);
        service = new ReconcileStuckWorkflowsService(
                workflowPort, tripoPendingTaskPort, workflowLockPort,
                workflowPollQueuePort, eventPublisher, properties,
                admissionQueuePort, admissionProperties);

        // 기본: withLock 은 즉시 action 실행
        doAnswer(inv -> {
            WorkflowLockPort.LockedAction action = inv.getArgument(1);
            action.run();
            return null;
        }).when(workflowLockPort).withLock(anyLong(), any());
    }

    private StuckWorkflow stuckPreparing(Instant heartbeat) {
        // PREPARING 상태에선 startedAt 이 null — REQUESTED→PREPARING 전이 시 초기화되지만
        // PREPARING heartbeat 갭 시나리오에선 의미 있는 값이 아님.
        return new StuckWorkflow(
                WORKFLOW_ID, SHOWCASE_ID, WorkflowStep.PREPARING,
                null, null, heartbeat, null, null, Instant.now().minusSeconds(300));
    }

    private WorkflowSnapshot preparingSnapshot(Instant heartbeat) {
        return new WorkflowSnapshot(
                WORKFLOW_ID, SHOWCASE_ID, "k", 1,
                WorkflowStep.PREPARING, null, null, Instant.now(), heartbeat, null, null);
    }

    @Nested
    @DisplayName("PREPARING 복구")
    class PreparingRecovery {

        @Test
        @DisplayName("tripo_pending_task 있음 → markGenerating 재개 + 이벤트 재발행")
        void pendingPresent_resumes() {
            Instant oldHeartbeat = Instant.now().minusSeconds(600);
            StuckWorkflow w = stuckPreparing(oldHeartbeat);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of(w));
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(preparingSnapshot(oldHeartbeat)));
            given(tripoPendingTaskPort.findTaskIdByWorkflowId(WORKFLOW_ID))
                    .willReturn(Optional.of(TASK_ID));
            given(workflowPort.markGenerating(WORKFLOW_ID, TASK_ID)).willReturn(1);
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            int tried = service.reconcileOnce();

            assertThat(tried).isEqualTo(1);
            verify(workflowPort, times(1)).markGenerating(WORKFLOW_ID, TASK_ID);
            verify(tripoPendingTaskPort, times(1)).deleteByWorkflowId(WORKFLOW_ID);
            verify(workflowPort, never())
                    .markFailed(anyLong(), any(), anyString(), anyString());
            verify(eventPublisher, times(1))
                    .publishEvent(new WorkflowGeneratingConfirmedEvent(WORKFLOW_ID));
        }

        @Test
        @DisplayName("tripo_pending_task 없음 → markFailed(TX2_DB_FAILED) + 이벤트 미발행")
        void pendingMissing_failsWorkflow() {
            Instant oldHeartbeat = Instant.now().minusSeconds(600);
            StuckWorkflow w = stuckPreparing(oldHeartbeat);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of(w));
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(preparingSnapshot(oldHeartbeat)));
            given(tripoPendingTaskPort.findTaskIdByWorkflowId(WORKFLOW_ID))
                    .willReturn(Optional.empty());
            given(workflowPort.markFailed(
                    eq(WORKFLOW_ID), eq(WorkflowFailureCode.TX2_DB_FAILED),
                    anyString(), eq("SCHEDULER"))).willReturn(1);
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            service.reconcileOnce();

            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID), eq(WorkflowFailureCode.TX2_DB_FAILED),
                    anyString(), eq("SCHEDULER"));
            verify(workflowPort, never()).markGenerating(anyLong(), anyString());
            verify(tripoPendingTaskPort, never()).deleteByWorkflowId(anyLong());
            // FAILED 로 종결한 워크플로우는 Poller 재큐 대상이 아님
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("heartbeat 복구된 경우 → skip (오판 방어) + 이벤트 미발행")
        void heartbeatRevived_skip() {
            Instant fresh = Instant.now();
            StuckWorkflow w = stuckPreparing(Instant.now().minusSeconds(600));
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of(w));
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(preparingSnapshot(fresh)));
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            service.reconcileOnce();

            verify(workflowPort, never()).markGenerating(anyLong(), anyString());
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(tripoPendingTaskPort, never()).findTaskIdByWorkflowId(anyLong());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("markGenerating affected=0 (선행 주체가 이미 전이) → 이벤트 미발행")
        void markGeneratingRaceLost_noEvent() {
            Instant oldHeartbeat = Instant.now().minusSeconds(600);
            StuckWorkflow w = stuckPreparing(oldHeartbeat);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of(w));
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(preparingSnapshot(oldHeartbeat)));
            given(tripoPendingTaskPort.findTaskIdByWorkflowId(WORKFLOW_ID))
                    .willReturn(Optional.of(TASK_ID));
            given(workflowPort.markGenerating(WORKFLOW_ID, TASK_ID)).willReturn(0);
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            service.reconcileOnce();

            verify(tripoPendingTaskPort, never()).deleteByWorkflowId(anyLong());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("락 busy → 조용히 skip (로그만)")
        void lockBusy_skip() {
            Instant oldHeartbeat = Instant.now().minusSeconds(600);
            StuckWorkflow w = stuckPreparing(oldHeartbeat);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of(w));
            doThrow(new WorkflowLockBusyException(WORKFLOW_ID))
                    .when(workflowLockPort).withLock(eq(WORKFLOW_ID), any());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            service.reconcileOnce();

            verify(workflowPort, never()).findSnapshot(anyLong());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("GENERATING·Tripo stuck 복구")
    class GeneratingTripoRecovery {

        @Test
        @DisplayName("DelayedQueue 에 delay=0 으로 재등록 (cap 미초과)")
        void redrivesToDelayedQueue() {
            // startedAt = now-3min — cap (5분) 미초과 → 기존 redrive 동작 유지
            StuckWorkflow w = new StuckWorkflow(
                    WORKFLOW_ID, SHOWCASE_ID, WorkflowStep.GENERATING,
                    TASK_ID, null, Instant.now(), Instant.now().minusSeconds(600),
                    Instant.now().minusSeconds(180), Instant.now().minusSeconds(900));
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt()))
                    .willReturn(List.of(w));
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            service.reconcileOnce();

            verify(workflowPollQueuePort, times(1)).offer(WORKFLOW_ID, Duration.ZERO);
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("GENERATING·Tripo lifetime cap")
    class LifetimeCap {

        private StuckWorkflow stuckWithStartedAt(Instant startedAt) {
            return new StuckWorkflow(
                    WORKFLOW_ID, SHOWCASE_ID, WorkflowStep.GENERATING,
                    TASK_ID, null, Instant.now(), Instant.now().minusSeconds(600),
                    startedAt, Instant.now().minusSeconds(900));
        }

        private WorkflowSnapshot tripoStuckSnapshot(Instant tripoSucceededAt, WorkflowStep step) {
            return new WorkflowSnapshot(
                    WORKFLOW_ID, SHOWCASE_ID, "k", 1,
                    step, TASK_ID, tripoSucceededAt,
                    Instant.now().minusSeconds(360), Instant.now(), null, null);
        }

        private void givenOnlyTripoStuck(StuckWorkflow w) {
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt()))
                    .willReturn(List.of(w));
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());
        }

        @Test
        @DisplayName("cap 초과 + 재검증 통과 → markFailed(TRIPO_TIMEOUT_GENERATING) + redrive 미발생")
        void capExceeded_marksFailedAndSkipsRedrive() {
            // startedAt = now-6min, cap=5min → 초과
            StuckWorkflow w = stuckWithStartedAt(Instant.now().minusSeconds(360));
            givenOnlyTripoStuck(w);
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(tripoStuckSnapshot(null, WorkflowStep.GENERATING)));
            given(workflowPort.markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.TRIPO_TIMEOUT_GENERATING),
                    anyString(),
                    eq("SCHEDULER"))).willReturn(1);

            service.reconcileOnce();

            verify(workflowLockPort, times(1)).withLock(eq(WORKFLOW_ID), any());
            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.TRIPO_TIMEOUT_GENERATING),
                    anyString(),
                    eq("SCHEDULER"));
            verify(workflowPollQueuePort, never()).offer(anyLong(), any(Duration.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("cap 초과 + markFailed affected=0 (이미 종결) → redrive 미발생")
        void capExceeded_alreadyTerminated_skipsRedrive() {
            StuckWorkflow w = stuckWithStartedAt(Instant.now().minusSeconds(360));
            givenOnlyTripoStuck(w);
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(tripoStuckSnapshot(null, WorkflowStep.GENERATING)));
            given(workflowPort.markFailed(
                    anyLong(), any(), anyString(), anyString())).willReturn(0);

            service.reconcileOnce();

            verify(workflowPort, times(1)).markFailed(
                    anyLong(), any(), anyString(), anyString());
            verify(workflowPollQueuePort, never()).offer(anyLong(), any(Duration.class));
        }

        @Test
        @DisplayName("cap 초과 + Poller 가 직전 SUCCESS 인지 (tripoSucceededAt != null) → cap 보류, markFailed 미호출")
        void capExceeded_pollerSucceededRace_skipsCap() {
            StuckWorkflow w = stuckWithStartedAt(Instant.now().minusSeconds(360));
            givenOnlyTripoStuck(w);
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(tripoStuckSnapshot(
                            Instant.now().minusSeconds(5), WorkflowStep.GENERATING)));

            service.reconcileOnce();

            verify(workflowLockPort, times(1)).withLock(eq(WORKFLOW_ID), any());
            // Poller SUCCESS 결과 폐기 방지 — markFailed 호출 안 함, redrive 도 안 함
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(workflowPollQueuePort, never()).offer(anyLong(), any(Duration.class));
        }

        @Test
        @DisplayName("cap 초과 + 재검증에서 currentStep 이미 종결 → cap 보류")
        void capExceeded_alreadyCompletedInSnapshot_skipsCap() {
            StuckWorkflow w = stuckWithStartedAt(Instant.now().minusSeconds(360));
            givenOnlyTripoStuck(w);
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(tripoStuckSnapshot(
                            Instant.now().minusSeconds(10), WorkflowStep.COMPLETED)));

            service.reconcileOnce();

            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(workflowPollQueuePort, never()).offer(anyLong(), any(Duration.class));
        }

        @Test
        @DisplayName("cap 초과 + 락 busy → markFailed 미호출, 다음 사이클 재시도")
        void capExceeded_lockBusy_skipsCap() {
            StuckWorkflow w = stuckWithStartedAt(Instant.now().minusSeconds(360));
            givenOnlyTripoStuck(w);
            doThrow(new WorkflowLockBusyException(WORKFLOW_ID))
                    .when(workflowLockPort).withLock(eq(WORKFLOW_ID), any());

            service.reconcileOnce();

            verify(workflowPort, never()).findSnapshot(anyLong());
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(workflowPollQueuePort, never()).offer(anyLong(), any(Duration.class));
        }

        @Test
        @DisplayName("startedAt = null → cap 미적용, 기존 redrive 동작 유지 (방어적)")
        void startedAtNull_capSkipped() {
            StuckWorkflow w = stuckWithStartedAt(null);
            givenOnlyTripoStuck(w);

            service.reconcileOnce();

            verify(workflowLockPort, never()).withLock(eq(WORKFLOW_ID), any());
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            verify(workflowPollQueuePort, times(1)).offer(WORKFLOW_ID, Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("GENERATING·S3 stuck 복구")
    class GeneratingS3Recovery {

        private StuckWorkflow s3StuckWorkflow(Instant heartbeat, String taskId) {
            // startedAt = now-200s — S3 미러링 단계는 lifetime cap 검사 대상이 아님 (Out 항목).
            return new StuckWorkflow(
                    WORKFLOW_ID, SHOWCASE_ID, WorkflowStep.GENERATING,
                    taskId, Instant.now().minusSeconds(400),
                    heartbeat, null, Instant.now().minusSeconds(200),
                    Instant.now().minusSeconds(1800));
        }

        private WorkflowSnapshot s3Snapshot(Instant heartbeat, Instant tripoSucceededAt,
                                            WorkflowStep step) {
            return new WorkflowSnapshot(
                    WORKFLOW_ID, SHOWCASE_ID, "k", 1,
                    step, TASK_ID, tripoSucceededAt,
                    Instant.now(), heartbeat, null, null);
        }

        @Test
        @DisplayName("재검증 통과 → TripoSuccessEvent 재발행 (Downloader 재시작)")
        void republishesTripoSuccess() {
            Instant oldHeartbeat = Instant.now().minusSeconds(400);
            StuckWorkflow w = s3StuckWorkflow(oldHeartbeat, TASK_ID);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt()))
                    .willReturn(List.of(w));
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(s3Snapshot(
                            oldHeartbeat, Instant.now().minusSeconds(400), WorkflowStep.GENERATING)));

            service.reconcileOnce();

            ArgumentCaptor<TripoSuccessEvent> captor = ArgumentCaptor.forClass(TripoSuccessEvent.class);
            verify(eventPublisher, times(1)).publishEvent(captor.capture());
            assertThat(captor.getValue()).isEqualTo(new TripoSuccessEvent(WORKFLOW_ID, TASK_ID));
        }

        @Test
        @DisplayName("이미 COMPLETED 로 전이됨 → 재발행 안 함 (중복 실행 방지)")
        void alreadyCompleted_noEvent() {
            Instant oldHeartbeat = Instant.now().minusSeconds(400);
            StuckWorkflow w = s3StuckWorkflow(oldHeartbeat, TASK_ID);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt()))
                    .willReturn(List.of(w));
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(s3Snapshot(
                            oldHeartbeat, Instant.now().minusSeconds(400), WorkflowStep.COMPLETED)));

            service.reconcileOnce();

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("heartbeat 복구됨 → 재발행 안 함")
        void heartbeatRevived_noEvent() {
            StuckWorkflow w = s3StuckWorkflow(Instant.now().minusSeconds(400), TASK_ID);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt()))
                    .willReturn(List.of(w));
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(s3Snapshot(
                            Instant.now(), Instant.now().minusSeconds(400), WorkflowStep.GENERATING)));

            service.reconcileOnce();

            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("tripo_task_id 누락 → 이벤트 미발행, 에러 로그만")
        void taskIdMissing_noEvent() {
            StuckWorkflow w = s3StuckWorkflow(Instant.now().minusSeconds(400), null);
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt()))
                    .willReturn(List.of(w));
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt())).willReturn(List.of());

            service.reconcileOnce();

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("REQUESTED 복구 (ADR-025)")
    class RequestedRecovery {

        private StuckWorkflow requestedStuck() {
            // REQUESTED 상태에선 startedAt 이 null — PREPARING 전이 시 초기화되기 때문.
            return new StuckWorkflow(
                    WORKFLOW_ID, SHOWCASE_ID, WorkflowStep.REQUESTED,
                    null, null, null, null, null, Instant.now().minusSeconds(120));
        }

        private void emptyOtherCategories() {
            given(workflowPort.findStuckPreparing(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingTripo(any(Instant.class), anyInt())).willReturn(List.of());
            given(workflowPort.findStuckGeneratingS3(any(Instant.class), anyInt())).willReturn(List.of());
        }

        @Test
        @DisplayName("입장 큐 미포함 → 경고 로그 + parkIfAbsent 재park, tried 합산 (Redrive — 직접 prepare 안 함)")
        void reparksWhenNotQueued() {
            emptyOtherCategories();
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt()))
                    .willReturn(List.of(requestedStuck()));
            given(admissionQueuePort.contains(WORKFLOW_ID)).willReturn(false);
            given(admissionQueuePort.parkIfAbsent(eq(WORKFLOW_ID), anyLong())).willReturn(true);

            int tried = service.reconcileOnce();

            assertThat(tried).isEqualTo(1);
            verify(admissionQueuePort, times(1)).parkIfAbsent(eq(WORKFLOW_ID), anyLong());
            // Redrive — Reconcile 은 직접 prepare/락/이벤트 하지 않고 drainer 에 위임
            verify(workflowLockPort, never()).withLock(anyLong(), any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("이미 입장 큐 대기 중 → 재park 안 함 (멱등), tried 0")
        void skipsWhenAlreadyQueued() {
            emptyOtherCategories();
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt()))
                    .willReturn(List.of(requestedStuck()));
            given(admissionQueuePort.contains(WORKFLOW_ID)).willReturn(true);

            int tried = service.reconcileOnce();

            assertThat(tried).isZero();
            verify(admissionQueuePort, never()).parkIfAbsent(anyLong(), anyLong());
            verify(workflowLockPort, never()).withLock(anyLong(), any());
        }

        @Test
        @DisplayName("enabled=false(롤백) → 경고 로그만, 재park 안 함 (도입 전 동작 복원)")
        void disabledRollback_logsOnlyNoRepark() {
            ReconcileProperties props = new ReconcileProperties(
                    true, 60_000L, 50, 60L, 8L, 5L, 30L, 5L);
            ReconcileStuckWorkflowsService disabledService = new ReconcileStuckWorkflowsService(
                    workflowPort, tripoPendingTaskPort, workflowLockPort,
                    workflowPollQueuePort, eventPublisher, props,
                    admissionQueuePort, new AdmissionQueueProperties(10, false));
            emptyOtherCategories();
            given(workflowPort.findStuckRequested(any(Instant.class), anyInt()))
                    .willReturn(List.of(requestedStuck()));

            int tried = disabledService.reconcileOnce();

            assertThat(tried).isZero();
            verify(admissionQueuePort, never()).contains(anyLong());
            verify(admissionQueuePort, never()).parkIfAbsent(anyLong(), anyLong());
        }
    }
}
