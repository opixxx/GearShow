package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;
import com.gearshow.backend.showcase.application.exception.ModelGenerationRetryableException;
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
 * <p>설계 결정 #1 (PREPARING 원자적 선행 전환) 반영:</p>
 * <ol>
 *   <li>updateStatusIfCurrent(REQUESTED → PREPARING) 원자적 전환 (1=성공, 0=실패)</li>
 *   <li>성공 시 findById 로 도메인 모델 조회</li>
 *   <li>Tripo startGeneration 호출</li>
 *   <li>성공 시 GENERATING + taskId 로 전환 (save 1회)</li>
 * </ol>
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

    /** PREPARING 상태의 도메인 객체 (원자적 전환 성공 후 findById 가 반환할 모델). */
    private Showcase3dModel preparingModel() {
        Instant now = Instant.now();
        return Showcase3dModel.builder()
                .id(MODEL_ID)
                .showcaseId(SHOWCASE_ID)
                .modelStatus(ModelStatus.PREPARING)
                .generationProvider(PROVIDER)
                .requestedAt(now)
                .createdAt(now)
                .retryCount(0)
                .build();
    }

    /** 원자적 전환 성공 + findById 반환을 함께 stub 하는 헬퍼. */
    private void stubAtomicTransitionSuccess() {
        given(showcase3dModelPort.updateStatusIfCurrent(
                MODEL_ID, ModelStatus.REQUESTED, ModelStatus.PREPARING)).willReturn(1);
        given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(preparingModel()));
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("원자적 PREPARING 전환 성공 → Tripo 성공 → GENERATING + taskId 저장")
        void prepare_atomicTransitionSuccess_savesGenerating() {
            // Given
            stubAtomicTransitionSuccess();
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            verify(showcase3dModelPort, times(1)).updateStatusIfCurrent(
                    MODEL_ID, ModelStatus.REQUESTED, ModelStatus.PREPARING);
            verify(modelGenerationClient, times(1)).startGeneration(MODEL_ID, SHOWCASE_ID);
            // save 1회: GENERATING 전환만 (PREPARING 전환은 updateStatusIfCurrent 로 처리)
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
        @DisplayName("원자적 전환 실패 (이미 다른 Worker 가 처리 중) → skip")
        void prepare_atomicTransitionFails_skips() {
            // Given — updateStatusIfCurrent 가 0 반환 (이미 PREPARING/GENERATING 등)
            given(showcase3dModelPort.updateStatusIfCurrent(
                    MODEL_ID, ModelStatus.REQUESTED, ModelStatus.PREPARING)).willReturn(0);

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
        @DisplayName("Circuit Breaker OPEN → UNAVAILABLE 전환")
        void prepare_circuitBreakerOpen_savesUnavailable() {
            // Given
            stubAtomicTransitionSuccess();
            CallNotPermittedException cbException = mock(CallNotPermittedException.class);
            willThrow(cbException)
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then — save 1회: UNAVAILABLE 전환
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            assertThat(captor.getValue().getModelStatus()).isEqualTo(ModelStatus.UNAVAILABLE);
        }

        @Test
        @DisplayName("ModelGenerationNonRetryableException → 즉시 FAILED (설계 결정 #4)")
        void prepare_nonRetryableException_savesFailed() {
            // Given
            stubAtomicTransitionSuccess();
            willThrow(new ModelGenerationNonRetryableException(ErrorCode.TRIPO_INSUFFICIENT_CREDIT, true))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            assertThat(captor.getValue().getModelStatus()).isEqualTo(ModelStatus.FAILED);
        }

        @Test
        @DisplayName("ModelGenerationRetryableException → PREPARING 유지, Recovery 대기 (설계 결정 #4)")
        void prepare_retryableException_keepsPreparing() {
            // Given
            stubAtomicTransitionSuccess();
            willThrow(new ModelGenerationRetryableException(ErrorCode.TRIPO_RATE_LIMITED))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then — save 0회: 상태 변경 없이 PREPARING 유지
            verify(showcase3dModelPort, never()).save(any());
        }

        @Test
        @DisplayName("RestClientException → FAILED 전환")
        void prepare_restClientException_savesFailed() {
            // Given
            stubAtomicTransitionSuccess();
            willThrow(new ResourceAccessException("connection reset"))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            assertThat(captor.getValue().getModelStatus()).isEqualTo(ModelStatus.FAILED);
        }

        @Test
        @DisplayName("DataAccessException → 예외 전파 (DLT 행)")
        void prepare_dataAccessException_propagates() {
            // Given
            stubAtomicTransitionSuccess();
            willThrow(new QueryTimeoutException("DB lock timeout"))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When & Then
            assertThatThrownBy(() -> service.prepare(MODEL_ID, SHOWCASE_ID))
                    .isInstanceOf(QueryTimeoutException.class);
        }
    }

    @Nested
    @DisplayName("Orphan task 방어")
    class OrphanTaskProtection {

        @Test
        @DisplayName("Tripo 성공 후 GENERATING 저장 실패 → orphan FAILED 마킹")
        void prepare_saveGeneratingFails_marksAsOrphanFailed() {
            // Given
            stubAtomicTransitionSuccess();
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);
            // save 1회(GENERATING) 실패, save 2회(FAILED orphan) 성공
            willThrow(new QueryTimeoutException("DB lock"))
                    .willAnswer(inv -> inv.getArgument(0))
                    .given(showcase3dModelPort).save(any(Showcase3dModel.class));

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then — save 2회: GENERATING 시도 + FAILED orphan 저장
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(2)).save(captor.capture());
            Showcase3dModel orphan = captor.getAllValues().get(1);
            assertThat(orphan.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(orphan.getFailureReason()).contains("orphan").contains(TASK_ID);
        }

        @Test
        @DisplayName("orphan 마킹마저 실패해도 예외 미전파 (무한 재시도 방지)")
        void prepare_orphanMarkingAlsoFails_swallows() {
            // Given
            stubAtomicTransitionSuccess();
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);
            willThrow(new QueryTimeoutException("DB completely down"))
                    .given(showcase3dModelPort).save(any(Showcase3dModel.class));

            // When & Then — 예외 전파 없음
            service.prepare(MODEL_ID, SHOWCASE_ID);
            verify(showcase3dModelPort, times(2)).save(any());
        }
    }
}
