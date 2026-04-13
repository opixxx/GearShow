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
            assertThat(model.getRetryCount()).isZero();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("REQUESTED에서 PREPARING으로 전이한다")
        void markPreparing_fromRequested_returnsPreparing() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When
            Showcase3dModel preparing = model.markPreparing();

            // Then
            assertThat(preparing.getModelStatus()).isEqualTo(ModelStatus.PREPARING);
            assertThat(preparing.getGenerationTaskId()).isNull();
            assertThat(preparing.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("PREPARING에서 task_id 와 함께 GENERATING으로 전이한다")
        void markGenerating_fromPreparing_returnsGeneratingWithTaskId() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

            // When
            Showcase3dModel generating = model.markGenerating(FAKE_TASK_ID);

            // Then
            assertThat(generating.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
            assertThat(generating.getGenerationTaskId()).isEqualTo(FAKE_TASK_ID);
        }

        @Test
        @DisplayName("REQUESTED에서 바로 GENERATING으로 전이하면 예외가 발생한다")
        void markGenerating_fromRequested_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(() -> model.markGenerating(FAKE_TASK_ID))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("markGenerating 에 task_id 를 누락하면 예외가 발생한다")
        void markGenerating_withoutTaskId_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

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
                    .markPreparing()
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
                    .markPreparing()
                    .markGenerating(FAKE_TASK_ID);

            // When
            Showcase3dModel failed = model.fail("이미지 품질 부족");

            // Then
            assertThat(failed.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo("이미지 품질 부족");
        }

        @Test
        @DisplayName("PREPARING에서 FAILED로 전이할 수 있다 (Tripo Non-retryable 에러)")
        void fail_fromPreparing_returnsFailedWithReason() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

            // When
            Showcase3dModel failed = model.fail("크레딧 부족");

            // Then
            assertThat(failed.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(failed.getFailureReason()).isEqualTo("크레딧 부족");
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
        @DisplayName("REQUESTED에서 UNAVAILABLE로 전이할 수 있다 (Circuit Breaker OPEN)")
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
        @DisplayName("PREPARING에서 UNAVAILABLE로 전이할 수 있다 (Circuit Breaker OPEN)")
        void markUnavailable_fromPreparing_returnsUnavailable() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

            // When
            Showcase3dModel unavailable = model.markUnavailable("Tripo 서비스 일시 이용 불가");

            // Then
            assertThat(unavailable.getModelStatus()).isEqualTo(ModelStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("COMPLETED에서 다른 상태로 전이하면 예외가 발생한다")
        void anyTransition_fromCompleted_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing()
                    .markGenerating(FAKE_TASK_ID)
                    .complete("url", "preview");

            // When & Then
            assertThatThrownBy(() -> model.markPreparing())
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
            assertThatThrownBy(() -> model.markGenerating("another-task"))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("PREPARING에서 바로 COMPLETED로 전이하면 예외가 발생한다")
        void complete_fromPreparing_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

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
                    .markPreparing()
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
            assertThat(reset.getRetryCount()).isZero();
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
                    .markPreparing()
                    .markGenerating(FAKE_TASK_ID)
                    .complete("url", "preview");

            // When & Then
            assertThatThrownBy(() -> model.resetRequest("tripo-v2"))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }
    }

    @Nested
    @DisplayName("resetForRetry")
    class ResetForRetry {

        @Test
        @DisplayName("PREPARING 상태에서 retryCount 증가 후 REQUESTED로 재설정한다")
        void resetForRetry_fromPreparing_returnsRequestedWithIncrementedRetry() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

            // When
            Showcase3dModel reset = model.resetForRetry("fake-tripo");

            // Then
            assertThat(reset.getModelStatus()).isEqualTo(ModelStatus.REQUESTED);
            assertThat(reset.getRetryCount()).isEqualTo(1);
            assertThat(reset.getGenerationTaskId()).isNull();
        }

        @Test
        @DisplayName("REQUESTED 상태에서 resetForRetry 하면 예외가 발생한다")
        void resetForRetry_fromRequested_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(() -> model.resetForRetry("fake-tripo"))
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("retryCount 가 3 이상이면 isMaxRetryExceeded 가 true 를 반환한다")
        void isMaxRetryExceeded_whenCountIs3_returnsTrue() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");
            Showcase3dModel retry1 = model.markPreparing().resetForRetry("fake-tripo");       // 1
            Showcase3dModel retry2 = retry1.markPreparing().resetForRetry("fake-tripo");       // 2
            Showcase3dModel retry3 = retry2.markPreparing().resetForRetry("fake-tripo");       // 3

            // When & Then
            assertThat(retry3.isMaxRetryExceeded()).isTrue();
        }
    }

    @Nested
    @DisplayName("markPolled")
    class MarkPolled {

        @Test
        @DisplayName("GENERATING 상태에서 markPolled 호출 시 lastPolledAt 이 갱신된다")
        void markPolled_whenGenerating_updatesLastPolledAt() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing()
                    .markGenerating(FAKE_TASK_ID);

            // When
            Showcase3dModel polled = model.markPolled();

            // Then
            assertThat(polled.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
            assertThat(polled.getLastPolledAt()).isNotNull();
            assertThat(polled.getGenerationTaskId()).isEqualTo(FAKE_TASK_ID);
        }

        @Test
        @DisplayName("REQUESTED 상태에서 markPolled 호출 시 예외가 발생한다")
        void markPolled_whenRequested_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo");

            // When & Then
            assertThatThrownBy(model::markPolled)
                    .isInstanceOf(InvalidShowcaseModelStatusTransitionException.class);
        }

        @Test
        @DisplayName("PREPARING 상태에서 markPolled 호출 시 예외가 발생한다")
        void markPolled_whenPreparing_throwsException() {
            // Given
            Showcase3dModel model = Showcase3dModel.request(1L, "fake-tripo")
                    .markPreparing();

            // When & Then
            assertThatThrownBy(model::markPolled)
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
                    .markPreparing()
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
