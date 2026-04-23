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
 * <p>P1-B-γ 이후: Outbox 자동 재발행 경로는 워크플로우/멱등성 키 연계 재설계(P1-G Reconcile)
 * 에서 복원되므로, 이 서비스는 현재 감지 + ALERT 로그 + (PREPARING 한정) 상태 되돌림만 수행한다.</p>
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

    private RecoverStuckModelsService service;

    private static final int PREPARING_STUCK_MINUTES = 2;

    @BeforeEach
    void setUp() {
        StuckRecoveryProperties properties = new StuckRecoveryProperties(
                60_000L, BATCH_SIZE, REQUESTED_STUCK_MINUTES, PREPARING_STUCK_MINUTES, GENERATING_STUCK_MINUTES);
        service = new RecoverStuckModelsService(showcase3dModelPort, properties);
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
    @DisplayName("REQUESTED stuck 감지")
    class DetectRequestedStuck {

        @Test
        @DisplayName("REQUESTED stuck 모델 N개를 감지하고 자동 재발행은 보류한다 (P1-G 대체 예정)")
        void recoverOnce_multipleStuckRequested_logsOnly() {
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

            // Then — 감지 카운터만 반환, 상태 변경 없음
            assertThat(recovered).isEqualTo(3);
            verify(showcase3dModelPort, never()).save(any());
        }
    }

    @Nested
    @DisplayName("PREPARING stuck 복구")
    class RecoverPreparingStuck {

        @Test
        @DisplayName("retryCount < 3 이면 REQUESTED 로 되돌린다 (Outbox 재등록은 P1-G 에서 복원)")
        void recoverOnce_preparingStuckLowRetry_resetsOnly() {
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
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("복구 대상이 없으면 0을 반환하고 save 호출이 없다")
        void recoverOnce_noTargets_returnsZero() {
            // Given
            stubAllEmpty();

            // When
            int recovered = service.recoverOnce();

            // Then
            assertThat(recovered).isZero();
            verify(showcase3dModelPort, never()).save(any());
        }
    }
}
