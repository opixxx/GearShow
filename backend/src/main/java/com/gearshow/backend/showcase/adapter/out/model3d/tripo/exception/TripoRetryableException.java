package com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception;

import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.application.exception.ModelGenerationRetryableException;

/**
 * Tripo API 일시적 장애 예외 (재시도 가능).
 *
 * <p>Application 계층의 {@link ModelGenerationRetryableException} 을 상속하여
 * PrepareModelGenerationService 가 Tripo 전용 예외를 알지 않아도 되게 한다.</p>
 */
public class TripoRetryableException extends ModelGenerationRetryableException {

    public TripoRetryableException(ErrorCode errorCode) {
        super(errorCode);
    }
}
