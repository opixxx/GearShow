package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 지원하지 않는 프로필 이미지 형식일 때 발생하는 예외.
 */
public class UnsupportedProfileImageTypeException extends CustomException {

    public UnsupportedProfileImageTypeException() {
        super(ErrorCode.USER_UNSUPPORTED_PROFILE_IMAGE_TYPE);
    }
}
