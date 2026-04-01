package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 유효하지 않은 사용자 상태 전이 시 발생하는 예외.
 */
public class InvalidUserStatusTransitionException extends CustomException {

    public InvalidUserStatusTransitionException() {
        super(ErrorCode.USER_INVALID_STATUS_TRANSITION);
    }
}
