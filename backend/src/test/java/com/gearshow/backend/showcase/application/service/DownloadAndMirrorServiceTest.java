package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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
 * {@link DownloadAndMirrorService} 단위 테스트.
 *
 * <p>검증 매트릭스:</p>
 * <ul>
 *   <li>Skip: 워크플로우 없음 / GENERATING 아님 / tripoSucceededAt null</li>
 *   <li>Happy: fetchResult 성공 → markCompleted affected=1 → showcase3dModel UPDATE + outbox publish</li>
 *   <li>재진입: markCompleted affected=0 → showcase 도메인 UPDATE·outbox 발행 없음</li>
 *   <li>fetchResult 예외 → 로그만, 락 진입 안 함</li>
 *   <li>락 busy → 예외 삼킴</li>
 *   <li>TX DB 실패 → 예외 삼킴 (Reconcile 복구)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DownloadAndMirrorService")
class DownloadAndMirrorServiceTest {

    private static final Long WORKFLOW_ID = 501L;
    private static final Long SHOWCASE_ID = 501_100L;
    private static final String TASK_ID = "tripo-task-501";
    private static final String MODEL_URL = "https://cdn.gearshow.com/models/501_100/model.glb";
    private static final String PREVIEW_URL = "https://cdn.gearshow.com/models/501_100/preview.png";

    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private ModelGenerationClient modelGenerationClient;
    @Mock
    private WorkflowLockPort workflowLockPort;
    @Mock
    private DownloadFinalizer downloadFinalizer;

    private DownloadAndMirrorService service;

    @BeforeEach
    void setUp() {
        service = new DownloadAndMirrorService(
                workflowPort, modelGenerationClient, workflowLockPort, downloadFinalizer);
        // 기본: 락 획득 성공 → action 즉시 실행
        doAnswer(invocation -> {
            WorkflowLockPort.LockedAction action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(workflowLockPort).withLock(anyLong(), any());
    }

    private WorkflowSnapshot generatingSnapshot(Instant tripoSucceededAt) {
        return new WorkflowSnapshot(
                WORKFLOW_ID, SHOWCASE_ID, "it-key", 1,
                WorkflowStep.GENERATING, TASK_ID, tripoSucceededAt,
                Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("Skip 경로")
    class SkipPaths {

        @Test
        @DisplayName("워크플로우 없음 → Tripo/DB 호출 없음")
        void noWorkflow_skips() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.empty());

            service.download(WORKFLOW_ID, TASK_ID);

            verify(modelGenerationClient, never()).fetchResult(anyString(), anyLong());
            verify(workflowPort, never()).markCompleted(anyLong());
        }

        @Test
        @DisplayName("currentStep != GENERATING → skip")
        void notGenerating_skips() {
            WorkflowSnapshot snap = new WorkflowSnapshot(
                    WORKFLOW_ID, SHOWCASE_ID, "k", 1,
                    WorkflowStep.COMPLETED, TASK_ID, Instant.now(),
                    Instant.now(), Instant.now());
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(snap));

            service.download(WORKFLOW_ID, TASK_ID);

            verify(modelGenerationClient, never()).fetchResult(anyString(), anyLong());
        }

        @Test
        @DisplayName("tripoSucceededAt null → skip (Poller SUCCESS 인지 전)")
        void tripoSucceededAtNull_skips() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(null)));

            service.download(WORKFLOW_ID, TASK_ID);

            verify(modelGenerationClient, never()).fetchResult(anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("Happy / 재진입")
    class HappyAndReentry {

        @BeforeEach
        void stubGenerating() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(Instant.now())));
            given(modelGenerationClient.fetchResult(TASK_ID, SHOWCASE_ID))
                    .willReturn(new GenerationResult(MODEL_URL, PREVIEW_URL));
        }

        @Test
        @DisplayName("Happy: 락 안에서 DownloadFinalizer.finalizeDownload 호출")
        void happy_delegatesToFinalizer() {
            service.download(WORKFLOW_ID, TASK_ID);

            verify(downloadFinalizer, times(1)).finalizeDownload(
                    eq(WORKFLOW_ID), eq(SHOWCASE_ID), any());
        }
    }

    @Nested
    @DisplayName("실패 경로")
    class FailurePaths {

        @Test
        @DisplayName("fetchResult RuntimeException: 락 진입 없음, finalize 호출 없음")
        void fetchResultThrows_noLockEntry() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(Instant.now())));
            given(modelGenerationClient.fetchResult(TASK_ID, SHOWCASE_ID))
                    .willThrow(new RuntimeException("Tripo 장애"));

            service.download(WORKFLOW_ID, TASK_ID);

            verify(workflowLockPort, never()).withLock(anyLong(), any());
            verify(downloadFinalizer, never()).finalizeDownload(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("락 busy: 예외 삼킴 — finalize 호출 없음")
        void lockBusy_swallowed() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(Instant.now())));
            given(modelGenerationClient.fetchResult(TASK_ID, SHOWCASE_ID))
                    .willReturn(new GenerationResult(MODEL_URL, PREVIEW_URL));
            doThrow(new WorkflowLockBusyException(WORKFLOW_ID))
                    .when(workflowLockPort).withLock(eq(WORKFLOW_ID), any());

            service.download(WORKFLOW_ID, TASK_ID);

            verify(downloadFinalizer, never()).finalizeDownload(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("Finalizer 가 IllegalStateException (도메인 행 누락) 던져도 호출자에 전파 없음")
        void finalizerDataIntegrityError_swallowed() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(Instant.now())));
            given(modelGenerationClient.fetchResult(TASK_ID, SHOWCASE_ID))
                    .willReturn(new GenerationResult(MODEL_URL, PREVIEW_URL));
            doThrow(new IllegalStateException("Showcase3dModel 행 누락"))
                    .when(downloadFinalizer).finalizeDownload(anyLong(), anyLong(), any());

            service.download(WORKFLOW_ID, TASK_ID);  // throw 금지
        }

        @Test
        @DisplayName("Finalizer 가 DataAccessException 던져도 호출자에 전파 없음 (Reconcile 복구)")
        void finalizerTxDbFail_swallowed() {
            given(workflowPort.findSnapshot(WORKFLOW_ID))
                    .willReturn(Optional.of(generatingSnapshot(Instant.now())));
            given(modelGenerationClient.fetchResult(TASK_ID, SHOWCASE_ID))
                    .willReturn(new GenerationResult(MODEL_URL, PREVIEW_URL));
            doThrow(new DataAccessResourceFailureException("DB 일시 오류"))
                    .when(downloadFinalizer).finalizeDownload(anyLong(), anyLong(), any());

            service.download(WORKFLOW_ID, TASK_ID);  // throw 금지
        }
    }
}
