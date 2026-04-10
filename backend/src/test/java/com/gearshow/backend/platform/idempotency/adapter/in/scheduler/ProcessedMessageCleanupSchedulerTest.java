package com.gearshow.backend.platform.idempotency.adapter.in.scheduler;

import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ProcessedMessageCleanupScheduler 단위 테스트.
 * 트리거가 정리 유스케이스를 정확히 위임 호출하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ProcessedMessageCleanupSchedulerTest {

    @Mock
    private CleanupProcessedMessageUseCase cleanupProcessedMessageUseCase;

    @InjectMocks
    private ProcessedMessageCleanupScheduler scheduler;

    @Nested
    @DisplayName("정상 처리")
    class HappyPath {

        @Test
        @DisplayName("스케줄러는 정리 유스케이스를 한 번 호출한다")
        void cleanupOldMessages_delegatesToUseCase() {
            // Given
            given(cleanupProcessedMessageUseCase.cleanup()).willReturn(42);

            // When
            scheduler.cleanupOldMessages();

            // Then
            verify(cleanupProcessedMessageUseCase, times(1)).cleanup();
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("정리 유스케이스가 예외를 던지면 스케줄러는 예외를 전파한다")
        void cleanupOldMessages_useCaseThrows_propagatesException() {
            // Given: Spring @Scheduled는 예외를 삼키면 다음 스케줄이 계속 돌지만
            // 운영 모니터링이 감지할 수 있도록 어댑터 레벨에서는 예외를 전파해야 한다
            willThrow(new QueryTimeoutException("DB 일시 장애"))
                    .given(cleanupProcessedMessageUseCase).cleanup();

            // When & Then
            assertThatThrownBy(() -> scheduler.cleanupOldMessages())
                    .isInstanceOf(QueryTimeoutException.class)
                    .hasMessageContaining("DB 일시 장애");

            verify(cleanupProcessedMessageUseCase, times(1)).cleanup();
        }
    }
}
