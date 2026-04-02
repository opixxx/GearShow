package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 대표 이미지가 지정되지 않았을 때 발생하는 예외.
 */
public class PrimaryImageRequiredException extends CustomException {

    public PrimaryImageRequiredException() {
        super(ErrorCode.SHOWCASE_PRIMARY_IMAGE_REQUIRED);
    }
}
