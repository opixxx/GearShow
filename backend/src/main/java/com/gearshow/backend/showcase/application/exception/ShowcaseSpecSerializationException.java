package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 스펙 JSON 직렬화에 실패한 경우 발생하는 예외.
 */
public class ShowcaseSpecSerializationException extends CustomException {

    public ShowcaseSpecSerializationException() {
        super(ErrorCode.SHOWCASE_SPEC_SERIALIZATION_FAILED);
    }
}
