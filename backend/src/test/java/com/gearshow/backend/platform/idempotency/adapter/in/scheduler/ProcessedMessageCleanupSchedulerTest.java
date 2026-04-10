package com.gearshow.backend.platform.idempotency.adapter.in.scheduler;

import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
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
