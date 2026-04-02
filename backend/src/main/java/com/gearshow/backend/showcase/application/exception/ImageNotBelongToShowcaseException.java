package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 이미지가 해당 쇼케이스에 속하지 않을 때 발생하는 예외.
 */
public class ImageNotBelongToShowcaseException extends CustomException {

    public ImageNotBelongToShowcaseException() {
        super(ErrorCode.SHOWCASE_IMAGE_NOT_BELONG);
    }
}
