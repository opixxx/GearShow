package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델이 이미 생성 중일 때 발생하는 예외.
 */
public class ModelAlreadyGeneratingException extends CustomException {

    public ModelAlreadyGeneratingException() {
        super(ErrorCode.SHOWCASE_MODEL_ALREADY_GENERATING);
    }
}
