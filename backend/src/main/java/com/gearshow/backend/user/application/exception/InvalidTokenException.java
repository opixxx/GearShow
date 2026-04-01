package com.gearshow.backend.user.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 유효하지 않은 토큰일 때 발생하는 예외.
 */
public class InvalidTokenException extends CustomException {

    public InvalidTokenException() {
        super(ErrorCode.AUTH_INVALID_TOKEN);
    }
}
