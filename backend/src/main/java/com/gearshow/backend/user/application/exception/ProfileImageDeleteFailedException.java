package com.gearshow.backend.user.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 프로필 이미지 삭제 실패 시 발생하는 예외.
 */
public class ProfileImageDeleteFailedException extends CustomException {

    public ProfileImageDeleteFailedException() {
        super(ErrorCode.USER_PROFILE_IMAGE_DELETE_FAILED);
    }
}
