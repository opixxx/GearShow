package com.gearshow.backend.user.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 프로필 이미지 업로드 실패 시 발생하는 예외.
 */
public class ProfileImageUploadFailedException extends CustomException {

    public ProfileImageUploadFailedException() {
        super(ErrorCode.USER_PROFILE_IMAGE_UPLOAD_FAILED);
    }
}
