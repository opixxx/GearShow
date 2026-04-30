package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Instant;

/**
 * 사용자가 하루 3D 모델 생성 quota 를 초과했을 때 발생하는 예외.
 *
 * <p>HTTP 응답으로 변환 시 429 Too Many Requests + {@code Retry-After} 헤더 (KST 다음 자정까지
 * 남은 초) 를 함께 보낸다 — {@code GlobalExceptionHandler} 가 {@code resetAt} 을 활용한다.</p>
 */
@Getter
public class DailyLimitExceededException extends CustomException {

    private final long limit;
    private final Instant resetAt;

    public DailyLimitExceededException(long limit, Instant resetAt) {
        super(ErrorCode.SHOWCASE_MODEL_DAILY_LIMIT_EXCEEDED);
        this.limit = limit;
        this.resetAt = resetAt;
    }
}
