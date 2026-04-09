package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 소유자가 아닌 경우 발생하는 예외.
 */
public class NotOwnerShowcaseException extends CustomException {

    public NotOwnerShowcaseException() {
        super(ErrorCode.SHOWCASE_NOT_OWNER);
    }
}
