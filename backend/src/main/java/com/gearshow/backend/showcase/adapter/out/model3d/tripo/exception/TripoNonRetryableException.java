package com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception;

import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.showcase.application.exception.ModelGenerationNonRetryableException;

/**
 * Tripo API 영구 실패 예외 (재시도 무의미).
 *
 * <p>Application 계층의 {@link ModelGenerationNonRetryableException} 을 상속하여
 * PrepareModelGenerationService 가 Tripo 전용 예외를 알지 않아도 되게 한다.</p>
 */
public class TripoNonRetryableException extends ModelGenerationNonRetryableException {

    public TripoNonRetryableException(ErrorCode errorCode, boolean alertRequired) {
        super(errorCode, alertRequired);
    }

    public TripoNonRetryableException(ErrorCode errorCode) {
        super(errorCode, false);
    }
}
