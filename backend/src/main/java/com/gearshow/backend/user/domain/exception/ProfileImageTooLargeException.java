package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 프로필 이미지 크기 초과 시 발생하는 예외.
 */
public class ProfileImageTooLargeException extends CustomException {

    public ProfileImageTooLargeException() {
        super(ErrorCode.USER_PROFILE_IMAGE_TOO_LARGE);
    }
}
