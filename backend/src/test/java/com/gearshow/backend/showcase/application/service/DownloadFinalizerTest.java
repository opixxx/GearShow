package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link DownloadFinalizer} 단위 테스트.
 *
 * <p><b>ADR-010 P1-G</b>: {@code markCompletedByShowcaseId} 분기가 제거되고 Showcase3dModel 은
 * UPSERT({@link Showcase3dModelPort#save}) 로 항상 기록된다. 검증 매트릭스도 이를 반영.</p>
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
    @DisplayName("Happy: markCompleted=1 → Showcase3dModel UPSERT + publishCompleted 호출, true 반환")
    void happy_publishesAndReturnsTrue() {
        given(workflowPort.markCompleted(WORKFLOW_ID)).willReturn(1);
        given(showcase3dModelPort.save(any(Showcase3dModel.class)))
                .willAnswer(inv -> inv.getArgument(0));

        boolean completed = finalizer.finalizeDownload(WORKFLOW_ID, SHOWCASE_ID, anyResult());

        assertThat(completed).isTrue();
        ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
        verify(showcase3dModelPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().getShowcaseId()).isEqualTo(SHOWCASE_ID);
        assertThat(captor.getValue().getModelFileUrl()).isEqualTo(MODEL_URL);
        assertThat(captor.getValue().getPreviewImageUrl()).isEqualTo(PREVIEW_URL);
        verify(eventPublisher, times(1)).publishCompleted(
                WORKFLOW_ID, SHOWCASE_ID, MODEL_URL, PREVIEW_URL);
    }

    @Test
    @DisplayName("재진입: markCompleted affected=0 → false, 이후 호출 없음")
    void workflowReentry_returnsFalse() {
        given(workflowPort.markCompleted(WORKFLOW_ID)).willReturn(0);

        boolean completed = finalizer.finalizeDownload(WORKFLOW_ID, SHOWCASE_ID, anyResult());

        assertThat(completed).isFalse();
        verify(showcase3dModelPort, never()).save(any(Showcase3dModel.class));
        verify(eventPublisher, never()).publishCompleted(
                anyLong(), anyLong(), anyString(), anyString());
    }
}
