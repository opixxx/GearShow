package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseModelStatusTransitionException;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Showcase3dModelTest {

    @Nested
    @DisplayName("request")
    class Request {

        @Test
        @DisplayName("3D 모델 생성을 요청하면 REQUESTED 상태이다")
        void request_returnsRequestedStatus() {
            // Given & When
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // Then
            assertThat(model.getModelStatus()).isEqualTo(ModelStatus.REQUESTED);
            assertThat(model.getShowcaseId()).isEqualTo(1L);
            assertThat(model.getGenerationProvider()).isEqualTo("fake-tripo");
            assertThat(model.getRequestedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("REQUESTED에서 GENERATING으로 전이한다")
        void startGenerating_fromRequested_returnsGenerating() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When
            Showcase3dModel generating = model.startGenerating();

            // Then
            assertThat(generating.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
        }

        @Test
        @DisplayName("GENERATING에서 COMPLETED로 전이하면 모델 URL이 설정된다")
        void complete_fromGenerating_returnsCompletedWithUrls() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo").startGenerating();

            // When
            Showcase3dModel completed = model.complete(
                    "https://cdn.gearshow.com/model.glb",
                    "https://cdn.gearshow.com/preview.jpg");

            // Then
            assertThat(completed.getModelStatus()).isEqualTo(ModelStatus.COMPLETED);
            assertThat(completed.getModelFileUrl()).isEqualTo("https://cdn.gearshow.com/model.glb");
            assertThat(completed.getPreviewImageUrl()).isEqualTo("https://cdn.gearshow.com/preview.jpg");
            assertThat(completed.getGeneratedAt()).isNotNull();
        }

        @Test
        @DisplayName("GENERATING에서 FAILED로 전이하면 실패 사유가 설정된다")
        void fail_fromGenerating_returnsFailedWithReason() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo").startGenerating();

            // When
            Showcase3dModel failed = model.fail("이미지 품질 부족");

            // Then
            assertThat(failed.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo("이미지 품질 부족");
        }

        @Test
        @DisplayName("COMPLETED에서 다른 상태로 전이하면 예외가 발생한다")
        void anyTransition_fromCompleted_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .startGenerating()
                    .complete("url", "preview");

            // When & Then
            assertThatThrownBy(model::startGenerating)
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("REQUESTED에서 바로 COMPLETED로 전이하면 예외가 발생한다")
        void complete_fromRequested_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(() -> model.complete("url", "preview"))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("REQUESTED에서 바로 FAILED로 전이하면 예외가 발생한다")
        void fail_fromRequested_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(() -> model.fail("실패"))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("isGenerating")
    class IsGenerating {

        @Test
        @DisplayName("GENERATING 상태이면 true를 반환한다")
        void isGenerating_whenGenerating_returnsTrue() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo").startGenerating();

            // When & Then
            assertThat(model.isGenerating()).isTrue();
        }

        @Test
        @DisplayName("REQUESTED 상태이면 false를 반환한다")
        void isGenerating_whenRequested_returnsFalse() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThat(model.isGenerating()).isFalse();
        }
    }
}
