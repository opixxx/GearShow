package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 이미지를 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundShowcaseImageException extends CustomException {

    public NotFoundShowcaseImageException() {
        super(ErrorCode.SHOWCASE_IMAGE_NOT_FOUND);
    }
}
