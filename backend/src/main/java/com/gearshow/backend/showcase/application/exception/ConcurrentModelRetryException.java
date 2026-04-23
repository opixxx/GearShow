package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 같은 쇼케이스에 대한 동시 재시도 요청이 감지됐을 때 발생하는 비즈니스 예외.
 *
 * <p>{@code uk_mgw_showcase_attempt} UNIQUE 제약 위반에서 매핑된다. 두 번째 요청자가
 * 409 Conflict 로 종료되어 "같은 순번의 workflow 가 두 개 생기는" 이력 오염을 막는다.
 * 클라이언트는 안전하게 재시도 가능 (서로 다른 {@code Idempotency-Key}).</p>
 */
public class ConcurrentModelRetryException extends CustomException {

    public ConcurrentModelRetryException() {
        super(ErrorCode.SHOWCASE_MODEL_RETRY_IN_PROGRESS);
    }
}
