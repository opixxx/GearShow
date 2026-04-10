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
        super(errorCode.getMessage());
        this.code = errorCode.name();
        this.status = errorCode.getStatus();
        this.message = errorCode.getMessage();
    }

    /**
     * 원인 예외(cause)를 함께 보존하는 생성자.
     * 외부 라이브러리 예외(예: JsonProcessingException)를 감쌀 때 사용한다.
     */
    public CustomException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.name();
        this.status = errorCode.getStatus();
        this.message = errorCode.getMessage();
    }
}
