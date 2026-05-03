package com.gearshow.backend.admin.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 새 비밀번호가 현재 비밀번호와 동일할 때 발생하는 예외.
 */
public class SameAsCurrentPasswordException extends CustomException {

    public SameAsCurrentPasswordException() {
        super(ErrorCode.ADMIN_PASSWORD_SAME_AS_CURRENT);
    }
}
