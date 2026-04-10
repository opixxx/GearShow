package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * PrepareModelGenerationService 단위 테스트.
 *
 * <p>Worker 에서 내부 유스케이스로 위임되는 "Tripo 호출 + 상태 전환" 로직을 검증한다.
 * 비즈니스 실패 시 예외 전파 대신 도메인 상태(FAILED/UNAVAILABLE) 전환이 이루어지는지가 핵심.</p>
 */
@ExtendWith(MockitoExtension.class)
class PrepareModelGenerationServiceTest {

    private static final Long MODEL_ID = 1L;
    private static final Long SHOWCASE_ID = 100L;
    private static final String PROVIDER = "fake-tripo";
    private static final String TASK_ID = "tripo-task-abc";

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ModelGenerationClient modelGenerationClient;

    @InjectMocks
    private PrepareModelGenerationService service;

    /**
     * id 가 세팅된 REQUESTED 상태의 도메인 객체를 생성한다.
     * Mockito stubbing 에서 기존 모델을 반환하는 시나리오용.
     */
    private Showcase3dModel existingRequestedModel() {
        Instant now = Instant.now();
        return Showcase3dModel.builder()
                .id(MODEL_ID)
                .showcaseId(SHOWCASE_ID)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider(PROVIDER)
                .requestedAt(now)
                .createdAt(now)
                .build();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("REQUESTED 모델에 대해 startGeneration 이 성공하면 GENERATING + taskId 로 저장된다")
        void prepare_requestedModelStartSuccess_savesGenerating() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            verify(modelGenerationClient, times(1)).startGeneration(MODEL_ID, SHOWCASE_ID);
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
            assertThat(saved.getGenerationTaskId()).isEqualTo(TASK_ID);
        }
    }

    @Nested
    @DisplayName("Skip 시나리오")
    class SkipScenarios {

        @Test
        @DisplayName("모델이 존재하지 않으면 Tripo 호출 없이 조용히 종료한다")
        void prepare_modelNotFound_doesNothing() {
            // Given
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.empty());

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
            verify(showcase3dModelPort, never()).save(any());
        }

        @Test
        @DisplayName("REQUESTED 가 아닌 상태(GENERATING) 이면 skip 한다 (재전달 방어)")
        void prepare_notRequested_skipsWithoutCallingTripo() {
            // Given
            Showcase3dModel generating = existingRequestedModel().markGenerating(TASK_ID);
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(generating));

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            verify(modelGenerationClient, never()).startGeneration(anyLong(), anyLong());
            verify(showcase3dModelPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("Tripo Circuit Breaker 가 OPEN 이면 UNAVAILABLE 로 전환되어 저장된다")
        void prepare_circuitBreakerOpen_savesUnavailable() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            // CallNotPermittedException 은 생성자가 public 이 아니므로 Mockito mock 으로 대체
            CallNotPermittedException cbException = mock(CallNotPermittedException.class);
            willThrow(cbException)
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.UNAVAILABLE);
            assertThat(saved.getFailureReason()).contains("이용 불가");
        }

        @Test
        @DisplayName("Tripo 호출에서 RestClientException 이 발생하면 FAILED 로 전환되고 고정된 사유가 저장된다")
        void prepare_restClientException_savesFailedWithFixedReason() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            willThrow(new ResourceAccessException("connection reset by peer"))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            // 고정된 사용자 노출 메시지 (내부 예외 메시지가 그대로 노출되지 않음)
            assertThat(saved.getFailureReason()).isEqualTo("3D 모델 생성을 시작하지 못했습니다");
            assertThat(saved.getFailureReason()).doesNotContain("connection reset");
        }

        @Test
        @DisplayName("DataAccessException 은 인프라 장애이므로 catch 하지 않고 그대로 전파한다 (DLT 이동 유도)")
        void prepare_dataAccessException_propagatesWithoutStatusChange() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            willThrow(new QueryTimeoutException("DB lock timeout"))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When & Then
            assertThatThrownBy(() -> service.prepare(MODEL_ID, SHOWCASE_ID))
                    .isInstanceOf(QueryTimeoutException.class);

            // 인프라 장애는 상태를 FAILED 로 오분류하지 않고 그대로 전파되어 DLT 가 처리하도록 한다
            verify(showcase3dModelPort, never()).save(any());
        }
    }
}
