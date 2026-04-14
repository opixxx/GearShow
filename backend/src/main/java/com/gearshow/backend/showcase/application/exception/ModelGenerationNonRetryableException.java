package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델 생성 중 영구 실패 예외 (재시도 무의미).
 *
 * <p>Application 계층에서 정의하여, 구체 어댑터(Tripo, Meshy 등)에 의존하지 않고
 * 즉시 FAILED 전환 여부를 판단할 수 있게 한다.</p>
 *
 * <p>크레딧 부족(2010), 인증 실패(1002) 등 전체 서비스에 영향을 주는 에러는
 * {@code alertRequired=true} 로 설정하여 개발자 Alert 를 유도한다.</p>
 */
public class ModelGenerationNonRetryableException extends CustomException {

    /** 개발자 Alert 가 필요한 에러인지 여부. 크레딧 부족, 인증 실패 시 true. */
    private final boolean alertRequired;

    public ModelGenerationNonRetryableException(ErrorCode errorCode, boolean alertRequired) {
        super(errorCode);
        this.alertRequired = alertRequired;
    }

    public ModelGenerationNonRetryableException(ErrorCode errorCode) {
        this(errorCode, false);
    }

    public boolean isAlertRequired() {
        return alertRequired;
    }
}
