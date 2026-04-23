package com.gearshow.backend.platform.idempotency.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 같은 {@code Idempotency-Key} 로 아직 처리 중인 요청이 존재하는 상태에서 재도달한 경우의 예외.
 */
public class ApiIdempotencyConflictException extends CustomException {

    public ApiIdempotencyConflictException() {
        super(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
    }
}
