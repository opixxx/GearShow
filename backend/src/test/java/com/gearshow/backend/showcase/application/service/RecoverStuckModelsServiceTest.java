package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
 * <p>설계 결정 #3 (Recovery 대상 명확화) 에 따른 세 가지 복구 경로:</p>
 * <ol>
 *   <li>REQUESTED stuck → Outbox 재등록</li>
 *   <li>PREPARING stuck → retryCount 기반 자동 재시도 또는 FAILED</li>
 *   <li>GENERATING + task_id 없음 (비정상) → 즉시 FAILED + Alert</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    /** 모든 findStale 쿼리를 빈 목록으로 stub 해둔다. 테스트별로 필요한 것만 override. */
    private void stubAllEmpty() {
        given(showcase3dModelPort.findStaleByStatus(
                eq(ModelStatus.REQUESTED), any(Instant.class), anyInt())).willReturn(List.of());
        given(showcase3dModelPort.findStaleByStatus(
                eq(ModelStatus.PREPARING), any(Instant.class), anyInt())).willReturn(List.of());
        given(showcase3dModelPort.findStaleGeneratingWithoutTaskId(
                any(Instant.class), anyInt())).willReturn(List.of());
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
                .retryCount(0)
                .build();
    }

    private Showcase3dModel preparingStuckModel(Long id, int retryCount) {
        Instant old = Instant.now().minusSeconds(600);
        return Showcase3dModel.builder()
                .id(id)
                .showcaseId(SHOWCASE_ID_BASE + id)
                .modelStatus(ModelStatus.PREPARING)
                .generationProvider("fake-tripo")
                .requestedAt(old)
                .createdAt(old)
                .retryCount(retryCount)
                .build();
    }

    private Showcase3dModel generatingAnomalous(Long id) {
        Instant old = Instant.now().minusSeconds(600);
        return Showcase3dModel.builder()
                .id(id)
                .showcaseId(SHOWCASE_ID_BASE + id)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider("fake-tripo")
                .generationTaskId(null)
                .requestedAt(old)
                .createdAt(old)
                .retryCount(0)
                .build();
    }

    @Nested
    @DisplayName("REQUESTED stuck 복구")
    class RecoverRequestedStuck {

        @Test
        @DisplayName("REQUESTED stuck 모델 N개에 대해 Publisher 가 N번 호출되고 총 복구 수가 반환된다")
        void recoverOnce_multipleStuckRequested_publishesEach() {
            // Given
            stubAllEmpty();
            List<Showcase3dModel> stuck = List.of(
                    requestedStuckModel(1L),
                    requestedStuckModel(2L),
                    requestedStuckModel(3L)
            );
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.REQUESTED), any(Instant.class), anyInt())).willReturn(stuck);

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isEqualTo(3);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(1L, 101L);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(2L, 102L);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(3L, 103L);
        }
    }

    @Nested
    @DisplayName("PREPARING stuck 복구")
    class RecoverPreparingStuck {

        @Test
        @DisplayName("retryCount < 3 이면 REQUESTED 로 되돌리고 Outbox 재등록")
        void recoverOnce_preparingStuckLowRetry_resetsAndPublishes() {
            // Given
            stubAllEmpty();
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.PREPARING), any(Instant.class), anyInt()))
                    .willReturn(List.of(preparingStuckModel(5L, 1)));

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isEqualTo(1);
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.REQUESTED);
            assertThat(saved.getRetryCount()).isEqualTo(2);
            verify(modelGenerationEventPublisher, times(1)).publishRequested(5L, 105L);
        }

        @Test
        @DisplayName("retryCount >= 3 이면 FAILED + Alert (무한 루프 방지)")
        void recoverOnce_preparingStuckMaxRetry_fails() {
            // Given
            stubAllEmpty();
            given(showcase3dModelPort.findStaleByStatus(
                    eq(ModelStatus.PREPARING), any(Instant.class), anyInt()))
                    .willReturn(List.of(preparingStuckModel(5L, 3)));

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isEqualTo(1);
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(saved.getFailureReason()).contains("재시도");
            // FAILED 이므로 Outbox 재등록 안 함
            verify(modelGenerationEventPublisher, never()).publishRequested(eq(5L), any());
        }
    }

    @Nested
    @DisplayName("GENERATING 비정상 감지")
    class AnomalousGenerating {

        @Test
        @DisplayName("GENERATING + task_id 없음 → 즉시 FAILED (비정상 상태)")
        void recoverOnce_anomalousGenerating_failsModel() {
            // Given
            stubAllEmpty();
            given(showcase3dModelPort.findStaleGeneratingWithoutTaskId(
                    any(Instant.class), anyInt()))
                    .willReturn(List.of(generatingAnomalous(10L)));

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isEqualTo(1);
            ArgumentCaptor<Showcase3dModel> captor = ArgumentCaptor.forClass(Showcase3dModel.class);
            verify(showcase3dModelPort, times(1)).save(captor.capture());
            Showcase3dModel saved = captor.getValue();
            assertThat(saved.getModelStatus()).isEqualTo(ModelStatus.FAILED);
            assertThat(saved.getFailureReason()).contains("비정상");
            verify(modelGenerationEventPublisher, never()).publishRequested(any(), any());
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("복구 대상이 없으면 0을 반환하고 Publisher/save 호출이 없다")
        void recoverOnce_noTargets_returnsZero() {
            // Given
            stubAllEmpty();

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isZero();
            verify(modelGenerationEventPublisher, never()).publishRequested(any(), any());
            verify(showcase3dModelPort, never()).save(any());
        }
    }
}
