package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 사용자 도메인 규칙을 위반했을 때 발생하는 예외.
 */
public class InvalidUserException extends CustomException {

    public InvalidUserException() {
        super(ErrorCode.USER_DUPLICATE_NICKNAME);
    }
}
