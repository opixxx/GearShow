package com.gearshow.backend.user.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 닉네임 중복 시 발생하는 예외.
 */
public class DuplicateNicknameException extends CustomException {

    public DuplicateNicknameException() {
        super(ErrorCode.USER_DUPLICATE_NICKNAME);
    }
}
