package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델 소스 이미지가 부족할 때 발생하는 예외.
 */
public class InsufficientModelSourceImagesException extends CustomException {

    public InsufficientModelSourceImagesException() {
        super(ErrorCode.SHOWCASE_MODEL_MIN_SOURCE_IMAGE_REQUIRED);
    }
}
