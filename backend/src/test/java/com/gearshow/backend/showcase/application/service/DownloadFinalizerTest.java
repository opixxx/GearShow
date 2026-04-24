package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link DownloadFinalizer} 단위 테스트.
 *
 * <p>검증 매트릭스:</p>
 * <ul>
 *   <li>Happy: markCompleted affected=1 + Showcase3dModel affected=1 → publishCompleted 호출 + true 반환</li>
 *   <li>workflow 재진입: markCompleted affected=0 → false 반환, 이후 호출 없음</li>
 *   <li>Showcase3dModel 누락: affected=0 → IllegalStateException 으로 TX 롤백, publishCompleted 미호출</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DownloadFinalizer")
class DownloadFinalizerTest {

    private static final Long WORKFLOW_ID = 701L;
    private static final Long SHOWCASE_ID = 701_100L;
    private static final String MODEL_URL = "https://cdn.gearshow.com/models/701_100/model.glb";
    private static final String PREVIEW_URL = "https://cdn.gearshow.com/models/701_100/preview.png";

    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private Showcase3dModelPort showcase3dModelPort;
    @Mock
    private ModelGenerationEventPublisher eventPublisher;

    @InjectMocks
    private DownloadFinalizer finalizer;

    private GenerationResult anyResult() {
        return new GenerationResult(MODEL_URL, PREVIEW_URL);
    }

    @Test
    @DisplayName("Happy: markCompleted=1 + Showcase3dModel=1 → publishCompleted 호출, true 반환")
    void happy_publishesAndReturnsTrue() {
        given(workflowPort.markCompleted(WORKFLOW_ID)).willReturn(1);
        given(showcase3dModelPort.markCompletedByShowcaseId(
                eq(SHOWCASE_ID), eq(MODEL_URL), eq(PREVIEW_URL), any(Instant.class)))
                .willReturn(1);

        boolean completed = finalizer.finalize(WORKFLOW_ID, SHOWCASE_ID, anyResult());

        assertThat(completed).isTrue();
        verify(eventPublisher, times(1)).publishCompleted(
                WORKFLOW_ID, SHOWCASE_ID, MODEL_URL, PREVIEW_URL);
    }

    @Test
    @DisplayName("재진입: markCompleted affected=0 → false, 이후 호출 없음")
    void workflowReentry_returnsFalse() {
        given(workflowPort.markCompleted(WORKFLOW_ID)).willReturn(0);

        boolean completed = finalizer.finalize(WORKFLOW_ID, SHOWCASE_ID, anyResult());

        assertThat(completed).isFalse();
        verify(showcase3dModelPort, never()).markCompletedByShowcaseId(
                anyLong(), anyString(), anyString(), any());
        verify(eventPublisher, never()).publishCompleted(
                anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Showcase3dModel 누락: affected=0 → IllegalStateException, publishCompleted 미호출")
    void showcaseModelMissing_throwsToRollback() {
        given(workflowPort.markCompleted(WORKFLOW_ID)).willReturn(1);
        given(showcase3dModelPort.markCompletedByShowcaseId(
                eq(SHOWCASE_ID), anyString(), anyString(), any(Instant.class)))
                .willReturn(0);

        assertThatThrownBy(() -> finalizer.finalize(WORKFLOW_ID, SHOWCASE_ID, anyResult()))
                .isInstanceOf(IllegalStateException.class);
        verify(eventPublisher, never()).publishCompleted(
                anyLong(), anyLong(), anyString(), anyString());
    }
}
