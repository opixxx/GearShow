package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.out.ProcessedMessagePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CleanupProcessedMessageService 단위 테스트.
 * 배치 루프 동작과 종료 조건을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CleanupProcessedMessageServiceTest {

    @Mock
    private ProcessedMessagePort processedMessagePort;

    @InjectMocks
    private CleanupProcessedMessageService service;

    private static final int BATCH_SIZE = 1000;

    @BeforeEach
    void setUpProperties() {
        // @Value 필드는 Mockito가 주입하지 않으므로 ReflectionTestUtils로 직접 설정한다
        ReflectionTestUtils.setField(service, "retentionDays", 7);
        ReflectionTestUtils.setField(service, "batchSize", BATCH_SIZE);
        ReflectionTestUtils.setField(service, "batchSleepMs", 0L); // 테스트 속도를 위해 sleep 제거
    }

    @Nested
    @DisplayName("정리 배치 루프")
    class CleanupBatchLoop {

        @Test
        @DisplayName("첫 배치에서 batchSize 미만이 삭제되면 한 번만 호출하고 종료한다")
        void cleanup_lessThanBatchSize_callsOnce() {
            // Given
            given(processedMessagePort.deleteBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(50);

            // When
            int totalDeleted = service.cleanup();

            // Then
            assertThat(totalDeleted).isEqualTo(50);
            verify(processedMessagePort, times(1)).deleteBatchOlderThan(any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("삭제할 행이 0건이면 한 번 호출 후 즉시 종료한다")
        void cleanup_nothingToDelete_callsOnce() {
            // Given
            given(processedMessagePort.deleteBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(0);

            // When
            int totalDeleted = service.cleanup();

            // Then
            assertThat(totalDeleted).isZero();
            verify(processedMessagePort, times(1)).deleteBatchOlderThan(any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("여러 배치에 걸쳐 삭제가 이어지면 batchSize 미만이 나올 때까지 반복 호출한다")
        void cleanup_multipleBatches_callsUntilLessThanBatchSize() {
            // Given: 1000, 1000, 500 → 총 3회 호출, 2,500건 삭제
            given(processedMessagePort.deleteBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(BATCH_SIZE)
                    .willReturn(BATCH_SIZE)
                    .willReturn(500);

            // When
            int totalDeleted = service.cleanup();

            // Then
            assertThat(totalDeleted).isEqualTo(2_500);
            verify(processedMessagePort, times(3)).deleteBatchOlderThan(any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("정확히 batchSize만큼 삭제되고 다음 배치가 0이면 두 번 호출 후 종료한다")
        void cleanup_exactBatchSizeThenZero_callsTwice() {
            // Given
            given(processedMessagePort.deleteBatchOlderThan(any(Instant.class), eq(BATCH_SIZE)))
                    .willReturn(BATCH_SIZE)
                    .willReturn(0);

            // When
            int totalDeleted = service.cleanup();

            // Then
            assertThat(totalDeleted).isEqualTo(BATCH_SIZE);
            verify(processedMessagePort, times(2)).deleteBatchOlderThan(any(Instant.class), anyInt());
        }
    }
}
