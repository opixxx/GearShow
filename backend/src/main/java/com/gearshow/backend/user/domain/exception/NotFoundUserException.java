package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundUserException extends CustomException {

    public NotFoundUserException() {
        super(ErrorCode.USER_NOT_FOUND);
    }
}
