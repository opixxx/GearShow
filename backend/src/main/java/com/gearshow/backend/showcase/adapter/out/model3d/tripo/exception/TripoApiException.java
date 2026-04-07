package com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception;

/**
 * Tripo API 호출 실패 시 발생하는 예외.
 */
public class TripoApiException extends RuntimeException {

    public TripoApiException(String message) {
        super(message);
    }

    public TripoApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
