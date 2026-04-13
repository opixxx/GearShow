package com.gearshow.backend.showcase.adapter.out.model3d.tripo.exception;

import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Tripo API 일시적 장애 예외 (재시도 가능).
 *
 * <p>다음 에러 코드에 해당:</p>
 * <ul>
 *   <li>429 / 1007: Rate limit exceeded</li>
 *   <li>429 / 2000: Generation limit exceeded</li>
 *   <li>500 / 1000, 1001: Server error</li>
 * </ul>
 *
 * <p>이 예외가 발생하면 모델은 PREPARING 상태를 유지하고,
 * Recovery 스케줄러가 retryCount 기반으로 자동 재시도한다.</p>
 */
public class TripoRetryableException extends TripoApiException {

    public TripoRetryableException(ErrorCode errorCode) {
        super(errorCode);
    }
}
