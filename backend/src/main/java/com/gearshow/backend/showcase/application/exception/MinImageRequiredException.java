package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 이미지가 최소 개수 미만일 때 발생하는 예외.
 */
public class MinImageRequiredException extends CustomException {

    public MinImageRequiredException() {
        super(ErrorCode.SHOWCASE_MIN_IMAGE_REQUIRED);
    }
}
