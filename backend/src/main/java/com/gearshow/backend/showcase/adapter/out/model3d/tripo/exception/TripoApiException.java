package com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Tripo API 호출 실패 시 발생하는 예외.
 */
public class TripoApiException extends CustomException {

    public TripoApiException(ErrorCode errorCode) {
        super(errorCode);
    }
}
