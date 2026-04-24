package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Tripo 세마포어 permit 을 대기하던 스레드가 인터럽트를 받은 경우 발생하는 예외.
 *
 * <p>{@link TripoSemaphoreTimeoutException} 과 의도적으로 분리된 별도 타입이다. 인터럽트는
 * "대기 시간 초과" 가 아니라 "스레드 종료 요청" 이므로 Retryable 로 분류하면 안 된다 — Kafka
 * {@code @RetryableTopic} 에 걸려 shutdown 중에도 재시도가 빠르게 소진되는 문제를 막기 위해
 * {@link ModelGenerationRetryableException} 을 상속하지 않고 일반
 * {@link CustomException} 으로 둔다. 상위 스케줄러는 이 예외를 받은 즉시 루프 종료로 해석한다.</p>
 */
public class SemaphoreInterruptedException extends CustomException {

    public SemaphoreInterruptedException() {
        super(ErrorCode.TRIPO_SEMAPHORE_TIMEOUT);
    }
}
