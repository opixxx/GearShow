package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 유효하지 않은 쇼케이스 상태 전이 시 발생하는 예외.
 */
public class InvalidShowcaseStatusTransitionException extends CustomException {

    public InvalidShowcaseStatusTransitionException() {
        super(ErrorCode.SHOWCASE_INVALID_STATUS_TRANSITION);
    }
}
