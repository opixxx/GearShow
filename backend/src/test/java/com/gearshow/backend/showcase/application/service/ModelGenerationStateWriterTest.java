package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ModelGenerationStateWriter 단위 테스트.
 *
 * <p>이 컴포넌트는 Spring {@code @Transactional} 의 self-invocation 문제를 피하기 위해
 * {@link PollGenerationStatusService} 에서 분리된 전용 쓰기 빈이다. 두 Aggregate
 * (showcase_3d_model + showcase) 동시 변경이 정상적으로 위임되는지 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class ModelGenerationStateWriterTest {

    private static final Long SHOWCASE_ID = 100L;
    private static final String TASK_ID = "tripo-task-abc";

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ShowcasePort showcasePort;

    @InjectMocks
    private ModelGenerationStateWriter stateWriter;

    /**
     * GENERATING 상태의 도메인 객체 생성 (ID 포함).
     */
    private Showcase3dModel generatingModel() {
        Instant now = Instant.now();
        return Showcase3dModel.builder()
                .id(1L)
                .showcaseId(SHOWCASE_ID)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider("fake-tripo")
                .generationTaskId(TASK_ID)
                .requestedAt(now)
                .createdAt(now)
                .build();
    }

    @Nested
    @DisplayName("markPolled")
    class MarkPolled {

        @Test
        @DisplayName("GENERATING 모델에 대해 save 가 호출되고 lastPolledAt 이 갱신된다")
        void markPolled_savesModelWithUpdatedPolledAt() {
            // Given
            Showcase3dModel model = generatingModel();

            // When
            stateWriter.markPolled(model);

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getLastPolledAt()).isNotNull();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
        }
    }

    @Nested
    @DisplayName("markCompleted")
    class MarkCompleted {

        @Test
        @DisplayName("모델 COMPLETED 전환 + showcase.has3dModel=true 가 함께 호출된다")
        void markCompleted_transitionsAndSyncsShowcase() {
            // Given
            Showcase3dModel model = generatingModel();
            GenerationResult result = new GenerationResult(
                    "https://cdn/m.glb", "https://cdn/p.jpg");

            // When
            stateWriter.markCompleted(model, result);

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.COMPLETED);
            assertThat(saved.getModelFileUrl()).isEqualTo("https://cdn/m.glb");
            assertThat(saved.getPreviewImageUrl()).isEqualTo("https://cdn/p.jpg");

            verify(showcasePort, times(1)).updateHas3dModel(SHOWCASE_ID, true);
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("모델 FAILED 전환 + showcase.has3dModel=false 가 함께 호출된다")
        void markFailed_transitionsAndSyncsShowcase() {
            // Given
            Showcase3dModel model = generatingModel();

            // When
            stateWriter.markFailed(model, "이미지 품질 부족");

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(saved.getFailureReason()).isEqualTo("이미지 품질 부족");

            verify(showcasePort, times(1)).updateHas3dModel(SHOWCASE_ID, false);
        }
    }
}
