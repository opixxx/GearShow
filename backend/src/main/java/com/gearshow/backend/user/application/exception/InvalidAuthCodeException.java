package com.gearshow.backend.user.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 유효하지 않은 인가 코드일 때 발생하는 예외.
 */
public class InvalidAuthCodeException extends CustomException {

    public InvalidAuthCodeException() {
        super(ErrorCode.AUTH_INVALID_CODE);
    }
}
