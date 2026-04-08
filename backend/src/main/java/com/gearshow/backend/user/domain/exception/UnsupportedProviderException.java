package com.gearshow.backend.user.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 지원하지 않는 소셜 로그인 제공자일 때 발생하는 예외.
 */
public class UnsupportedProviderException extends CustomException {

    public UnsupportedProviderException() {
        super(ErrorCode.AUTH_UNSUPPORTED_PROVIDER);
    }
}
