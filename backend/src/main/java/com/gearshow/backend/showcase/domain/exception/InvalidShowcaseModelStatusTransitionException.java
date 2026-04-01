package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 유효하지 않은 3D 모델 상태 전이 시 발생하는 예외.
 */
public class InvalidShowcaseModelStatusTransitionException extends CustomException {

    public InvalidShowcaseModelStatusTransitionException() {
        super(ErrorCode.SHOWCASE_MODEL_ALREADY_GENERATING);
    }
}
