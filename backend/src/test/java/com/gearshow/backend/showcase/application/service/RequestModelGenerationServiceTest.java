package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link RequestModelGenerationService} 단위 테스트 (ADR-010 프로세스 분리 반영).
 *
 * <p>P1-G 이후 이 서비스는 완성품 {@code Showcase3dModel} INSERT 를 하지 않는다.
 * 소스 이미지 + 워크플로우 + Outbox 이벤트 기록만 수행한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequestModelGenerationService")
class RequestModelGenerationServiceTest {

    private static final Long SHOWCASE_ID = 10L;
    private static final String IDEMPOTENCY_KEY = "req-key-0001";
    private static final List<String> IMAGE_URLS = List.of(
            "https://cdn/a.jpg", "https://cdn/b.jpg", "https://cdn/c.jpg", "https://cdn/d.jpg");

    @Mock
    private ModelSourceImagePort modelSourceImagePort;
    @Mock
    private ModelGenerationWorkflowPort modelGenerationWorkflowPort;
    @Mock
    private ModelGenerationEventPublisher modelGenerationEventPublisher;

    @InjectMocks
    private RequestModelGenerationService service;

    @BeforeEach
    void setUp() {
        given(modelSourceImagePort.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("saveSourceImagesAndRequest: workflow(attempt=1) INSERT + 소스 이미지 + Outbox")
    void saveSourceImagesAndRequest_initial() {
        given(modelGenerationWorkflowPort.saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 1))
                .willReturn(777L);

        ModelGenerationResult result = service.saveSourceImagesAndRequest(
                SHOWCASE_ID, IDEMPOTENCY_KEY, IMAGE_URLS);

        assertThat(result.workflowId()).isEqualTo(777L);
        assertThat(result.modelStatus()).isEqualTo(ShowcaseModelStatus.GENERATING);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelSourceImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(modelSourceImagePort, times(1)).saveAll(imagesCaptor.capture());
        assertThat(imagesCaptor.getValue()).hasSize(IMAGE_URLS.size());
        assertThat(imagesCaptor.getValue())
                .allSatisfy(img -> assertThat(img.getShowcaseId()).isEqualTo(SHOWCASE_ID));

        verify(modelGenerationWorkflowPort, times(1))
                .saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 1);
        verify(modelGenerationEventPublisher, times(1)).publishRequested(
                eq(777L), eq(SHOWCASE_ID), eq(IDEMPOTENCY_KEY));
    }

    @Test
    @DisplayName("resetSourceImagesAndRequestRetry: nextAttemptNo 계산 후 새 워크플로우 INSERT")
    void resetSourceImagesAndRequestRetry_increasesAttempt() {
        given(modelGenerationWorkflowPort.nextAttemptNo(SHOWCASE_ID)).willReturn(3);
        given(modelGenerationWorkflowPort.saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 3))
                .willReturn(888L);

        ModelGenerationResult result = service.resetSourceImagesAndRequestRetry(
                SHOWCASE_ID, IDEMPOTENCY_KEY, IMAGE_URLS);

        assertThat(result.workflowId()).isEqualTo(888L);
        assertThat(result.modelStatus()).isEqualTo(ShowcaseModelStatus.GENERATING);
        verify(modelGenerationWorkflowPort, times(1)).nextAttemptNo(SHOWCASE_ID);
        verify(modelGenerationWorkflowPort, times(1))
                .saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 3);
        verify(modelGenerationEventPublisher, times(1)).publishRequested(
                eq(888L), anyLong(), eq(IDEMPOTENCY_KEY));
    }
}
