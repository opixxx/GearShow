package com.gearshow.backend.platform.outbox.application.service;

import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.infrastructure.config.OutboxRelayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CleanupOutboxService 단위 테스트.
 *
 * <p>배치 삭제 루프 로직을 검증한다. 루프 종료 조건(deleted < batchSize)과
 * 총 삭제 수 집계, sleep 우회(sleepMs=0) 등의 흐름이 정확한지가 핵심.</p>
 */
@ExtendWith(MockitoExtension.class)
class CleanupOutboxServiceTest {

    private static final int BATCH_SIZE = 100;
    private static final int RETENTION_DAYS = 7;

    @Mock
    private OutboxMessagePort outboxMessagePort;

    private CleanupOutboxService service;

    @BeforeEach
    void setUp() {
        // sleepMs = 0 으로 설정해 테스트가 실제로 sleep 하지 않도록 한다
        OutboxRelayProperties properties = new OutboxRelayProperties(
                1_000L, BATCH_SIZE, 5_000L,
                "0 0 4 * * *", "Asia/Seoul",
                RETENTION_DAYS, BATCH_SIZE, 0L);
        service = new CleanupOutboxService(outboxMessagePort, properties);
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("한 번의 배치로 모두 삭제되면 루프가 즉시 종료되고 삭제 수가 반환된다")
        void cleanup_singleBatchUnderSize_returnsDeletedCount() {
            // Given: 50건 삭제 (batchSize=100 미만 → 루프 종료)
            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(50);

            // When
            int deleted = service.cleanup();

            // Then
            assertThat(deleted).isEqualTo(50);
            verify(outboxMessagePort, times(1))
                    .deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("여러 배치에 걸쳐 삭제되면 정확히 합산된다")
        void cleanup_multipleBatches_sumsAllDeletions() {
            // Given: 1회차 100 (batchSize 만큼 → 계속), 2회차 100 (계속), 3회차 30 (미만 → 종료)
            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(BATCH_SIZE)
                    .willReturn(BATCH_SIZE)
                    .willReturn(30);

            // When
            int deleted = service.cleanup();

            // Then
            assertThat(deleted).isEqualTo(BATCH_SIZE + BATCH_SIZE + 30);
            verify(outboxMessagePort, times(3))
                    .deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE));
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("삭제 대상이 없으면 0을 반환하고 1회만 쿼리한다")
        void cleanup_noTargets_returnsZero() {
            // Given
            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(0);

            // When
            int deleted = service.cleanup();

            // Then
            assertThat(deleted).isZero();
            verify(outboxMessagePort, times(1))
                    .deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE));
        }
    }

    @Nested
    @DisplayName("임계값 계산")
    class ThresholdCalculation {

        @Test
        @DisplayName("retentionDays 설정값 기준의 과거 시각이 threshold 로 사용된다")
        void cleanup_passesRetentionThresholdInstant() {
            // Given
            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(0);
            Instant before = Instant.now().minusSeconds(RETENTION_DAYS * 24L * 60 * 60 + 10);
            Instant after = Instant.now().minusSeconds(RETENTION_DAYS * 24L * 60 * 60 - 10);

            // When
            service.cleanup();

            // Then — threshold 가 "retentionDays 전 ± 10초" 범위에 있어야 한다
            org.mockito.ArgumentCaptor<Instant> captor = org.mockito.ArgumentCaptor.forClass(Instant.class);
            verify(outboxMessagePort).deletePublishedBatchOlderThan(captor.capture(), eq(BATCH_SIZE));
            Instant threshold = captor.getValue();
            assertThat(threshold).isAfter(before).isBefore(after);
        }
    }

    @Nested
    @DisplayName("루프 종료 조건")
    class LoopTermination {

        @Test
        @DisplayName("deleted 가 batchSize 보다 작으면 즉시 종료 (더 이상 호출되지 않음)")
        void cleanup_deletedLessThanBatch_stopsLoop() {
            // Given: 첫 번째 호출에서 batchSize - 1 반환 → 종료
            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(BATCH_SIZE - 1);

            // When
            int deleted = service.cleanup();

            // Then
            assertThat(deleted).isEqualTo(BATCH_SIZE - 1);
            verify(outboxMessagePort, times(1))
                    .deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE));
            verify(outboxMessagePort, never()) // 추가 호출 없음
                    .save(any());
        }
    }

    @Nested
    @DisplayName("배치 사이 sleep")
    class SleepBetweenBatches {

        @Test
        @DisplayName("sleepMs > 0 이고 여러 배치를 처리하면 실제 sleep 을 수행한다")
        void cleanup_multipleBatchesWithRealSleep_sleepsBetweenBatches() {
            // Given: sleepMs 10 (짧게 설정)
            OutboxRelayProperties propertiesWithSleep = new OutboxRelayProperties(
                    1_000L, BATCH_SIZE, 5_000L,
                    "0 0 4 * * *", "Asia/Seoul",
                    RETENTION_DAYS, BATCH_SIZE, 10L);
            CleanupOutboxService serviceWithSleep = new CleanupOutboxService(
                    outboxMessagePort, propertiesWithSleep);

            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(BATCH_SIZE)      // 1회차: 계속
                    .willReturn(50);              // 2회차: 종료

            // When
            int deleted = serviceWithSleep.cleanup();

            // Then
            assertThat(deleted).isEqualTo(BATCH_SIZE + 50);
            verify(outboxMessagePort, times(2))
                    .deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE));
        }

        @Test
        @DisplayName("루프 도중 인터럽트가 발생하면 루프를 조기 종료한다")
        void cleanup_threadInterrupted_exitsLoopEarly() throws InterruptedException {
            // Given: 5ms 단위 sleep, 첫 배치에서 full 반환 후 2배치 시작 전에 인터럽트
            OutboxRelayProperties propertiesWithSleep = new OutboxRelayProperties(
                    1_000L, BATCH_SIZE, 5_000L,
                    "0 0 4 * * *", "Asia/Seoul",
                    RETENTION_DAYS, BATCH_SIZE, 5L);
            CleanupOutboxService serviceWithSleep = new CleanupOutboxService(
                    outboxMessagePort, propertiesWithSleep);

            // 첫 호출 중 현재 스레드 인터럽트 설정 → Thread.sleep 이 InterruptedException 던짐
            given(outboxMessagePort.deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willAnswer(invocation -> {
                        Thread.currentThread().interrupt();
                        return BATCH_SIZE; // 계속 조건 만족시켜서 sleep 경로 타게 함
                    });

            // When
            int deleted = serviceWithSleep.cleanup();

            // Then — sleep 중 인터럽트로 루프 조기 종료
            assertThat(deleted).isEqualTo(BATCH_SIZE);
            assertThat(Thread.interrupted()).isTrue(); // 인터럽트 플래그가 재설정됨
            verify(outboxMessagePort, times(1))
                    .deletePublishedBatchOlderThan(any(Instant.class), eq(BATCH_SIZE));
        }
    }
}
