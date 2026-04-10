package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import com.gearshow.backend.showcase.infrastructure.config.StuckRecoveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * RecoverStuckModelsService 단위 테스트.
 *
 * <p>두 가지 복구 경로를 검증한다:</p>
 * <ol>
 *   <li>REQUESTED stuck → Outbox 재등록 (Publisher 호출)</li>
 *   <li>GENERATING 좀비(task_id 없음) → FAILED 로 강제 전환</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RecoverStuckModelsServiceTest {

    private static final int BATCH_SIZE = 50;
    private static final int REQUESTED_STUCK_MINUTES = 5;
    private static final int GENERATING_STUCK_MINUTES = 5;
    private static final Long SHOWCASE_ID_BASE = 100L;

    @Mock
    private Showcase3dModelPort showcase3dModelPort;

    @Mock
    private ModelGenerationEventPublisher modelGenerationEventPublisher;

    private RecoverStuckModelsService service;

    @BeforeEach
    void setUp() {
        StuckRecoveryProperties properties = new StuckRecoveryProperties(
                60_000L, BATCH_SIZE, REQUESTED_STUCK_MINUTES, GENERATING_STUCK_MINUTES);
        service = new RecoverStuckModelsService(
                showcase3dModelPort, modelGenerationEventPublisher, properties);
    }

    private Showcase3dModel requestedStuckModel(Long id) {
        Instant old = Instant.now().minusSeconds(600);
        return Showcase3dModel.builder()
                .id(id)
                .showcaseId(SHOWCASE_ID_BASE + id)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider("fake-tripo")
                .requestedAt(old)
                .createdAt(old)
                .build();
    }

    private Showcase3dModel generatingZombie(Long id) {
        // task_id 가 없는 좀비
        Instant old = Instant.now().minusSeconds(600);
        return Showcase3dModel.builder()
                .id(id)
                .showcaseId(SHOWCASE_ID_BASE + id)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider("fake-tripo")
                .generationTaskId(null)
                .requestedAt(old)
                .createdAt(old)
                .build();
    }

    private Showcase3dModel generatingHealthy(Long id) {
        // task_id 가 있는 정상 GENERATING — 폴링 스케줄러 소관이므로 복구 대상 아님
        Instant old = Instant.now().minusSeconds(600);
        return Showcase3dModel.builder()
                .id(id)
                .showcaseId(SHOWCASE_ID_BASE + id)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider("fake-tripo")
                .generationTaskId("tripo-task-" + id)
                .requestedAt(old)
                .createdAt(old)
                .build();
    }

    @Nested
    @DisplayName("REQUESTED stuck 복구")
    class RecoverRequestedStuck {

        @Test
        @DisplayName("REQUESTED stuck 모델 N개에 대해 Publisher 가 N번 호출되고 총 복구 수가 반환된다")
        void recoverOnce_multipleStuckRequested_publishesEach() {
            // Given
            List<Showcase3dModel> stuck = List.of(
                    requestedStuckModel(1L),
                    requestedStuckModel(2L),
                    requestedStuckModel(3L)
            );
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.REQUESTED), any(Instant.class), anyInt())).willReturn(stuck);
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.GENERATING), any(Instant.class), anyInt())).willReturn(List.of());

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isEqualTo(3);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(1L, 101L);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(2L, 102L);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(3L, 103L);
            // GENERATING 쪽은 비어있으므로 save 호출 없음
            verify(showcase3dModelPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("GENERATING 좀비 복구")
    class RecoverGeneratingZombie {

        @Test
        @DisplayName("task_id 가 없는 좀비는 FAILED 로 전환되어 저장된다")
        void recoverOnce_zombieGenerating_failsModel() {
            // Given
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.REQUESTED), any(Instant.class), anyInt())).willReturn(List.of());
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.GENERATING), any(Instant.class), anyInt()))
                    .willReturn(List.of(generatingZombie(10L)));

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isEqualTo(1);
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(saved.getFailureReason()).contains("Worker 크래시");
            verify(modelGenerationEventPublisher, never()).publishRequested(any(), any());
        }

        @Test
        @DisplayName("GENERATING 후보 중 task_id 가 있는 정상 모델은 skip 된다 (폴링 스케줄러 소관)")
        void recoverOnce_generatingWithTaskId_isSkipped() {
            // Given
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.REQUESTED), any(Instant.class), anyInt())).willReturn(List.of());
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.GENERATING), any(Instant.class), anyInt()))
                    .willReturn(List.of(
                            generatingZombie(10L),
                            generatingHealthy(11L),  // task_id 있음 → skip
                            generatingZombie(12L)
                    ));

            // When
            int recovered = service.recoverOnce();

            // Then: 좀비 2건만 FAILED 전환
            assertThat(recovered).isEqualTo(2);
            verify(showcase3dModelPort, times(2)).save(any());
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("복구 대상이 없으면 0을 반환하고 Publisher/save 호출이 없다")
        void recoverOnce_noTargets_returnsZero() {
            // Given
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.REQUESTED), any(Instant.class), anyInt())).willReturn(List.of());
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.GENERATING), any(Instant.class), anyInt())).willReturn(List.of());

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isZero();
            verify(modelGenerationEventPublisher, never()).publishRequested(any(), any());
            verify(showcase3dModelPort, never()).save(any());
        }
    }
}
