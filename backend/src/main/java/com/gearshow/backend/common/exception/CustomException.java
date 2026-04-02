package com.gearshow.backend.common.exception;

import lombok.Getter;

/**
 * 모든 커스텀 예외의 기반 클래스.
 * 반드시 {@link ErrorCode}를 통해 생성해야 한다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final String code;
    private final int status;
    private final String message;

    public CustomException(ErrorCode errorCode) {
        this.code = errorCode.name();
        this.status = errorCode.getStatus();
        this.message = errorCode.getMessage();
    }
}
