package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.port.out.ImageStoragePort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
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

import java.util.List;
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
 * {@link PrepareWorkflowService} 단위 테스트.
 *
 * <p>TX1 불변식 검증:</p>
 * <ul>
 *   <li>workflow 없음 → skip (조건부 UPDATE 호출 안 함)</li>
 *   <li>조건부 UPDATE affected=0 (이미 처리 중) → skip</li>
 *   <li>소스 이미지 < 4 → {@code SOURCE_IMAGES_MISSING}</li>
 *   <li>S3 누락 1건 → {@code S3_KEY_MISSING}, 나머지 체크 중단</li>
 *   <li>Happy path: 4장 존재 확인 후 정상 종료 (markFailed 호출 없음)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PrepareWorkflowService")
class PrepareWorkflowServiceTest {

    private static final Long WORKFLOW_ID = 77L;
    private static final Long SHOWCASE_ID = 77_100L;

    @Mock
    private ModelGenerationWorkflowPort workflowPort;

    @Mock
    private ModelSourceImagePort modelSourceImagePort;

    @Mock
    private ImageStoragePort imageStoragePort;

    @Mock
    private WorkflowLockPort workflowLockPort;

    private PrepareWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new PrepareWorkflowService(
                workflowPort, modelSourceImagePort, imageStoragePort, workflowLockPort);
        // 기본: 락 획득 성공 → action 즉시 실행 (락 없는 것처럼 동작)
        doAnswer(invocation -> {
            WorkflowLockPort.LockedAction action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(workflowLockPort).withLock(anyLong(), any());
    }

    private WorkflowSnapshot requestedSnapshot() {
        return new WorkflowSnapshot(
                WORKFLOW_ID, SHOWCASE_ID, "it-key", 1,
                WorkflowStep.REQUESTED, null, null);
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
        }

        @Test
        @DisplayName("REQUESTED→PREPARING 전환이 affected=0 이면 검증 없이 skip 한다")
        void alreadyTransitioned_skipsValidation() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            given(workflowPort.updateStepIfCurrent(WORKFLOW_ID, WorkflowStep.REQUESTED, WorkflowStep.PREPARING))
                    .willReturn(0);

            service.prepare(WORKFLOW_ID);

            verify(modelSourceImagePort, never()).findImageUrlsByShowcaseId(anyLong());
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("분산 락이 busy 면 조건부 UPDATE 도 호출하지 않고 skip 한다")
        void lockBusy_skipsWithoutAnyWrite() {
            given(workflowPort.findSnapshot(WORKFLOW_ID)).willReturn(Optional.of(requestedSnapshot()));
            doThrow(new WorkflowLockBusyException(WORKFLOW_ID))
                    .when(workflowLockPort).withLock(eq(WORKFLOW_ID), any());

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, never()).updateStepIfCurrent(anyLong(), any(), any());
            verify(modelSourceImagePort, never()).findImageUrlsByShowcaseId(anyLong());
            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
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
        @DisplayName("소스 이미지가 4장 미만이면 SOURCE_IMAGES_MISSING 로 FAILED 마킹한다")
        void fewerThanFour_marksFailed() {
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID))
                    .willReturn(List.of("a.jpg", "b.jpg", "c.jpg"));

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.SOURCE_IMAGES_MISSING),
                    anyString(),
                    eq("INTERNAL"));
            verify(imageStoragePort, never()).existsByUrl(anyString());
        }

        @Test
        @DisplayName("S3 객체 1개라도 누락되면 S3_KEY_MISSING 으로 FAILED 마킹하고 이후 체크를 중단한다")
        void missingS3Object_marksFailedAndStops() {
            List<String> urls = List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg");
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(urls);
            given(imageStoragePort.existsByUrl("a.jpg")).willReturn(true);
            given(imageStoragePort.existsByUrl("b.jpg")).willReturn(false);

            service.prepare(WORKFLOW_ID);

            verify(imageStoragePort, times(1)).existsByUrl("a.jpg");
            verify(imageStoragePort, times(1)).existsByUrl("b.jpg");
            verify(imageStoragePort, never()).existsByUrl("c.jpg");
            verify(workflowPort, times(1)).markFailed(
                    eq(WORKFLOW_ID),
                    eq(WorkflowFailureCode.S3_KEY_MISSING),
                    anyString(),
                    eq("S3"));
        }

        @Test
        @DisplayName("Happy path: 4장 모두 존재하면 markFailed 호출하지 않고 정상 종료")
        void allPresent_noFailureMark() {
            List<String> urls = List.of("a.jpg", "b.jpg", "c.jpg", "d.jpg");
            given(modelSourceImagePort.findImageUrlsByShowcaseId(SHOWCASE_ID)).willReturn(urls);
            given(imageStoragePort.existsByUrl(anyString())).willReturn(true);

            service.prepare(WORKFLOW_ID);

            verify(workflowPort, never()).markFailed(anyLong(), any(), anyString(), anyString());
            urls.forEach(url -> verify(imageStoragePort, times(1)).existsByUrl(url));
        }
    }
}
