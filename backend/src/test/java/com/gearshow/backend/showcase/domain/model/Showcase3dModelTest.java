package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidGenerationTaskIdException;
import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseModelStatusTransitionException;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Showcase3dModelTest {

    private static final String FAKE_TASK_ID = "task-abc123";

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
            assertThat(model.getGenerationTaskId()).isNull();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("REQUESTED에서 task_id 와 함께 GENERATING으로 전이한다")
        void markGenerating_fromRequested_returnsGeneratingWithTaskId() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When
            Showcase3dModel generating = model.markGenerating(FAKE_TASK_ID);

            // Then
            assertThat(generating.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
            assertThat(generating.getGenerationTaskId()).isEqualTo(FAKE_TASK_ID);
        }

        @Test
        @DisplayName("markGenerating 에 task_id 를 누락하면 예외가 발생한다")
        void markGenerating_withoutTaskId_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(() -> model.markGenerating(""))
                    .isInstanceOf(InvalidGenerationTaskIdException.class);
            assertThatThrownBy(() -> model.markGenerating(null))
                    .isInstanceOf(InvalidGenerationTaskIdException.class);
        }

        @Test
        @DisplayName("GENERATING에서 COMPLETED로 전이하면 모델 URL이 설정된다")
        void complete_fromGenerating_returnsCompletedWithUrls() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markGenerating(FAKE_TASK_ID);

            // When
            Showcase3dModel completed = model.complete(
                    "https://cdn.gearshow.com/model.glb",
                    "https://cdn.gearshow.com/preview.jpg");

            // Then
            assertThat(completed.getModelStatus()).isEqualTo(ModelStatus.COMPLETED);
            assertThat(completed.getModelFileUrl()).isEqualTo("https://cdn.gearshow.com/model.glb");
            assertThat(completed.getPreviewImageUrl()).isEqualTo("https://cdn.gearshow.com/preview.jpg");
            assertThat(completed.getGeneratedAt()).isNotNull();
            // task_id 는 완료 후에도 유지되어야 한다 (감사/디버깅용)
            assertThat(completed.getGenerationTaskId()).isEqualTo(FAKE_TASK_ID);
        }

        @Test
        @DisplayName("GENERATING에서 FAILED로 전이하면 실패 사유가 설정된다")
        void fail_fromGenerating_returnsFailedWithReason() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markGenerating(FAKE_TASK_ID);

            // When
            Showcase3dModel failed = model.fail("이미지 품질 부족");

            // Then
            assertThat(failed.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo("이미지 품질 부족");
        }

        @Test
        @DisplayName("REQUESTED에서 FAILED로 전이할 수 있다 (Tripo 호출 전 실패)")
        void fail_fromRequested_returnsFailedWithReason() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When
            Showcase3dModel failed = model.fail("네트워크 오류");

            // Then
            assertThat(failed.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo("네트워크 오류");
        }

        @Test
        @DisplayName("REQUESTED에서 UNAVAILABLE로 전이할 수 있다 (Tripo Circuit Breaker OPEN)")
        void markUnavailable_fromRequested_returnsUnavailable() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When
            Showcase3dModel unavailable = model.markUnavailable("Tripo 서비스 일시 이용 불가");

            // Then
            assertThat(unavailable.getModelStatus()).isEqualTo(ModelStatus.UNAVAILABLE);
            assertThat(unavailable.getFailureReason()).isEqualTo("Tripo 서비스 일시 이용 불가");
        }

        @Test
        @DisplayName("COMPLETED에서 다른 상태로 전이하면 예외가 발생한다")
        void anyTransition_fromCompleted_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markGenerating(FAKE_TASK_ID)
                    .complete("url", "preview");

            // When & Then
            assertThatThrownBy(() -> model.markGenerating("another-task"))
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
    }

    @Nested
    @DisplayName("resetRequest")
    class ResetRequest {

        @Test
        @DisplayName("FAILED 상태에서 REQUESTED로 재설정한다")
        void resetRequest_fromFailed_returnsRequested() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markGenerating(FAKE_TASK_ID)
                    .fail("이미지 품질 부족");

            // When
            Showcase3dModel reset = model.resetRequest("tripo-v2");

            // Then
            assertThat(reset.getModelStatus()).isEqualTo(ModelStatus.REQUESTED);
            assertThat(reset.getGenerationProvider()).isEqualTo("tripo-v2");
            assertThat(reset.getRequestedAt()).isNotNull();
            assertThat(reset.getId()).isEqualTo(model.getId());
            assertThat(reset.getGenerationTaskId()).isNull();
        }

        @Test
        @DisplayName("UNAVAILABLE 상태에서 REQUESTED로 재설정한다 (사용자 수동 재시도)")
        void resetRequest_fromUnavailable_returnsRequested() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markUnavailable("Tripo 서비스 일시 이용 불가");

            // When
            Showcase3dModel reset = model.resetRequest("fake-tripo");

            // Then
            assertThat(reset.getModelStatus()).isEqualTo(ModelStatus.REQUESTED);
        }

        @Test
        @DisplayName("REQUESTED 상태에서 재설정하면 예외가 발생한다")
        void resetRequest_fromRequested_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(() -> model.resetRequest("tripo-v2"))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 재설정하면 예외가 발생한다")
        void resetRequest_fromCompleted_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markGenerating(FAKE_TASK_ID)
                    .complete("url", "preview");

            // When & Then
            assertThatThrownBy(() -> model.resetRequest("tripo-v2"))
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
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markGenerating(FAKE_TASK_ID);

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
