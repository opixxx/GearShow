package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SemaphoreInterruptedException")
class SemaphoreInterruptedExceptionTest {

    @Test
    @DisplayName("CustomException 을 상속하고 ErrorCode.TRIPO_SEMAPHORE_TIMEOUT 을 보유한다")
    void extendsCustomExceptionWithTripoSemaphoreErrorCode() {
        SemaphoreInterruptedException exception = new SemaphoreInterruptedException();

        assertThat(exception)
                .isInstanceOf(CustomException.class)
                .isInstanceOf(RuntimeException.class);
        assertThat(exception.getCode()).isEqualTo(ErrorCode.TRIPO_SEMAPHORE_TIMEOUT.name());
        assertThat(exception.getStatus()).isEqualTo(ErrorCode.TRIPO_SEMAPHORE_TIMEOUT.getStatus());
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.TRIPO_SEMAPHORE_TIMEOUT.getMessage());
    }

    @Test
    @DisplayName("ModelGenerationRetryableException 과 별개 타입이다 — @RetryableTopic 재시도 대상 제외")
    void isNotRetryable() {
        SemaphoreInterruptedException exception = new SemaphoreInterruptedException();

        assertThat(exception).isNotInstanceOf(ModelGenerationRetryableException.class);
    }
}
