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
 * <p>설계 결정 #1 (PREPARING 선행 전환) 과 #4 (Tripo 에러 분류) 가 반영된 흐름:</p>
 * <ol>
 *   <li>REQUESTED 확인 → PREPARING 으로 선행 전환 (save #1)</li>
 *   <li>Tripo startGeneration 호출</li>
 *   <li>성공 시 GENERATING + taskId 로 전환 (save #2)</li>
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

    /** id 가 세팅된 REQUESTED 상태의 도메인 객체를 생성한다. */
    private Showcase3dModel existingRequestedModel() {
        Instant now = Instant.now();
        return Showcase3dModel.builder()
                .id(MODEL_ID)
                .showcaseId(SHOWCASE_ID)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider(PROVIDER)
                .requestedAt(now)
                .createdAt(now)
                .retryCount(0)
                .build();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("REQUESTED 모델에 대해 PREPARING 전환 후 startGeneration 성공 시 GENERATING + taskId 로 저장된다")
        void prepare_requestedModelStartSuccess_savesGenerating() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then
            verify(modelGenerationClient, times(1)).startGeneration(MODEL_ID, SHOWCASE_ID);
            // save 2회: PREPARING 전환 (1) + GENERATING 전환 (2)
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(2)).save(captor.capture());

            Showcase3dModel preparingSave = captor.getAllValues().get(0);
            assertThat(preparingSave.getModelStatus()).isEqualTo(ModelStatus.PREPARING);

            Showcase3dModel generatingSave = captor.getAllValues().get(1);
            assertThat(generatingSave.getModelStatus()).isEqualTo(ModelStatus.GENERATING);
            assertThat(generatingSave.getGenerationTaskId()).isEqualTo(TASK_ID);
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
        @DisplayName("REQUESTED 가 아닌 상태(PREPARING) 이면 skip 한다 (재전달 방어)")
        void prepare_notRequested_skipsWithoutCallingTripo() {
            // Given — 이미 PREPARING 상태인 모델
            Showcase3dModel preparing = existingRequestedModel().markPreparing();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(preparing));

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
        @DisplayName("Tripo Circuit Breaker OPEN 시 UNAVAILABLE 로 전환 (PREPARING 거쳐서)")
        void prepare_circuitBreakerOpen_savesUnavailable() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            CallNotPermittedException cbException = mock(CallNotPermittedException.class);
            willThrow(cbException)
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then — save 2회: PREPARING (1) + UNAVAILABLE (2)
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(2)).save(captor.capture());

            Showcase3dModel saved = captor.getAllValues().get(1);
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.UNAVAILABLE);
            assertThat(saved.getFailureReason()).contains("이용 불가");
        }

        @Test
        @DisplayName("RestClientException 발생 시 FAILED 전환 (PREPARING 거쳐서)")
        void prepare_restClientException_savesFailedWithFixedReason() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            willThrow(new ResourceAccessException("connection reset by peer"))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then — save 2회: PREPARING (1) + FAILED (2)
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(2)).save(captor.capture());

            Showcase3dModel saved = captor.getAllValues().get(1);
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(saved.getFailureReason()).isEqualTo("3D 모델 생성을 시작하지 못했습니다");
            assertThat(saved.getFailureReason()).doesNotContain("connection reset");
        }

        @Test
        @DisplayName("DataAccessException 은 PREPARING 전환 후 DLT 로 전파한다")
        void prepare_dataAccessException_propagatesAfterPreparing() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            willThrow(new QueryTimeoutException("DB lock timeout"))
                    .given(modelGenerationClient).startGeneration(MODEL_ID, SHOWCASE_ID);

            // When & Then
            assertThatThrownBy(() -> service.prepare(MODEL_ID, SHOWCASE_ID))
                    .isInstanceOf(QueryTimeoutException.class);

            // PREPARING 전환을 위한 save 1회는 호출됨
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            assertThat(captor.getValue().getModelStatus()).isEqualTo(ModelStatus.PREPARING);
        }
    }

    @Nested
    @DisplayName("Orphan task 방어")
    class OrphanTaskProtection {

        @Test
        @DisplayName("Tripo 성공 후 GENERATING 저장 실패 시 orphan FAILED 마킹")
        void prepare_saveGeneratingFails_marksAsOrphanFailed() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);
            // save 1회(PREPARING) 성공, save 2회(GENERATING) 실패, save 3회(FAILED orphan) 성공
            given(showcase3dModelPort.save(any(Showcase3dModel.class)))
                    .willAnswer(inv -> inv.getArgument(0))   // PREPARING 성공
                    .willThrow(new QueryTimeoutException("DB lock"))  // GENERATING 실패
                    .willAnswer(inv -> inv.getArgument(0));   // FAILED orphan 성공

            // When
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // Then — save 3회: PREPARING (1) + GENERATING 시도 (2) + FAILED orphan (3)
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(3)).save(captor.capture());

            Showcase3dModel orphanFailed = captor.getAllValues().get(2);
            assertThat(orphanFailed.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(orphanFailed.getFailureReason())
                    .contains("orphan")
                    .contains(TASK_ID);
        }

        @Test
        @DisplayName("orphan 마킹마저 실패해도 예외를 전파하지 않는다")
        void prepare_orphanMarkingAlsoFails_swallowsException() {
            // Given
            Showcase3dModel model = existingRequestedModel();
            given(showcase3dModelPort.findById(MODEL_ID)).willReturn(Optional.of(model));
            given(modelGenerationClient.startGeneration(MODEL_ID, SHOWCASE_ID)).willReturn(TASK_ID);
            // PREPARING 성공, 이후 GENERATING/FAILED 저장 모두 실패
            given(showcase3dModelPort.save(any(Showcase3dModel.class)))
                    .willAnswer(inv -> inv.getArgument(0))   // PREPARING 성공
                    .willThrow(new QueryTimeoutException("DB completely down"))  // GENERATING 실패
                    .willThrow(new QueryTimeoutException("DB completely down")); // orphan 도 실패

            // When & Then
            service.prepare(MODEL_ID, SHOWCASE_ID);

            // save 3회: PREPARING (1) + GENERATING (2) + FAILED orphan (3)
            verify(showcase3dModelPort, times(3)).save(any());
        }
    }
}
