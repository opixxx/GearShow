package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 인증 계정 도메인 규칙을 위반했을 때 발생하는 예외.
 */
public class InvalidAuthAccountException extends CustomException {

    public InvalidAuthAccountException() {
        super(ErrorCode.AUTH_ACCOUNT_NOT_FOUND);
    }
}
