package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Tripo 동시 요청 제한 세마포어 획득 실패 예외.
 *
 * <p>Redis {@code tripo:semaphore} (10 permits) 에서 대기 시간 초과 시 발생한다.
 * {@link ModelGenerationRetryableException} 을 상속해 Worker 경로에서는
 * {@code @RetryableTopic} 이 자동 backoff 재시도하도록 하고, Poller 경로에서는
 * {@code WorkflowPollingScheduler} 가 짧은 delay 로 다시 {@code pollQueue.offer} 한다.</p>
 */
public class TripoSemaphoreTimeoutException extends ModelGenerationRetryableException {

    public TripoSemaphoreTimeoutException() {
        super(ErrorCode.TRIPO_SEMAPHORE_TIMEOUT);
    }
}
