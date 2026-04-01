package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스를 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundShowcaseException extends CustomException {

    public NotFoundShowcaseException() {
        super(ErrorCode.SHOWCASE_NOT_FOUND);
    }
}
