package com.gearshow.backend.platform.idempotency.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 다른 사용자가 등록한 {@code Idempotency-Key} 를 요청 사용자가 재사용하려 한 경우의 예외.
 * 캐싱된 응답의 IDOR 가능성을 차단한다.
 */
public class ApiIdempotencyOwnershipMismatchException extends CustomException {

    public ApiIdempotencyOwnershipMismatchException() {
        super(ErrorCode.IDEMPOTENCY_OWNERSHIP_MISMATCH);
    }
}
