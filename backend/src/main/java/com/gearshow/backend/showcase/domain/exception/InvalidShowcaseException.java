package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 도메인 규칙을 위반했을 때 발생하는 예외.
 */
public class InvalidShowcaseException extends CustomException {

    public InvalidShowcaseException() {
        super(ErrorCode.SHOWCASE_INVALID);
    }
}
