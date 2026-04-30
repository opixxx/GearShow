package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import com.gearshow.backend.showcase.domain.vo.AngleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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

    @Test
    @DisplayName("resetSourceImagesAndRequestRetry: deleteByShowcaseId 가 saveAll 보다 먼저 호출")
    void resetSourceImagesAndRequestRetry_deletesBeforeSave() {
        given(modelGenerationWorkflowPort.nextAttemptNo(SHOWCASE_ID)).willReturn(2);
        given(modelGenerationWorkflowPort.saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 2))
                .willReturn(901L);

        service.resetSourceImagesAndRequestRetry(SHOWCASE_ID, IDEMPOTENCY_KEY, IMAGE_URLS);

        // 핵심: 옛 4행 hard delete → 새 4행 saveAll 순서. 누적 8행 방지.
        InOrder order = inOrder(modelSourceImagePort);
        order.verify(modelSourceImagePort).deleteByShowcaseId(SHOWCASE_ID);
        order.verify(modelSourceImagePort).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("saveSourceImagesAndRequest (신규): deleteByShowcaseId 호출 없음")
    void saveSourceImagesAndRequest_doesNotDelete() {
        given(modelGenerationWorkflowPort.saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 1))
                .willReturn(777L);

        service.saveSourceImagesAndRequest(SHOWCASE_ID, IDEMPOTENCY_KEY, IMAGE_URLS);

        verify(modelSourceImagePort, never()).deleteByShowcaseId(anyLong());
    }

    @Test
    @DisplayName("이미지 URL 4장이 [FRONT, LEFT, BACK, RIGHT] 순서로 angleType 매핑된다 (Tripo 입력 순서 일치)")
    void saveSourceImages_mapsAngleTypeInTripoOrder() {
        given(modelGenerationWorkflowPort.saveRequested(SHOWCASE_ID, IDEMPOTENCY_KEY, 1))
                .willReturn(777L);

        service.saveSourceImagesAndRequest(SHOWCASE_ID, IDEMPOTENCY_KEY, IMAGE_URLS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ModelSourceImage>> imagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(modelSourceImagePort, times(1)).saveAll(imagesCaptor.capture());

        List<ModelSourceImage> saved = imagesCaptor.getValue();
        assertThat(saved).hasSize(4);
        // Tripo multiview_to_model 입력 순서 [front, left, back, right] 와 정확히 일치해야 한다.
        // enum 선언 순서 (FRONT, BACK, LEFT, RIGHT) 와 다르므로 회귀 방지 명시적 검증.
        assertThat(saved.get(0).getAngleType()).isEqualTo(AngleType.FRONT);
        assertThat(saved.get(1).getAngleType()).isEqualTo(AngleType.LEFT);
        assertThat(saved.get(2).getAngleType()).isEqualTo(AngleType.BACK);
        assertThat(saved.get(3).getAngleType()).isEqualTo(AngleType.RIGHT);
        // sortOrder 도 1..4 로 결정적 부여
        assertThat(saved.get(0).getSortOrder()).isEqualTo(1);
        assertThat(saved.get(3).getSortOrder()).isEqualTo(4);
    }
}
