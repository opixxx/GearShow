package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델 생성 중 일시적 장애 예외 (재시도 가능).
 *
 * <p>Application 계층에서 정의하여, 구체 어댑터(Tripo, Meshy 등)에 의존하지 않고
 * 재시도 가능 여부를 판단할 수 있게 한다. 어댑터는 이 예외를 상속하거나 직접 throw 한다.</p>
 *
 * <p>이 예외가 발생하면 모델은 PREPARING 상태를 유지하고,
 * Recovery 스케줄러가 retryCount 기반으로 자동 재시도한다.</p>
 */
public class ModelGenerationRetryableException extends CustomException {

    public ModelGenerationRetryableException(ErrorCode errorCode) {
        super(errorCode);
    }
}
