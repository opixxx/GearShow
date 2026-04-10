package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델 상태 머신에서 허용되지 않는 전이를 시도했을 때 발생하는 예외.
 *
 * <p>"이미 생성 중" 과는 의미가 다르며(=이미 같은 상태라 변경 불필요),
 * 이 예외는 "현재 상태에서 목표 상태로 전이할 수 없는 경우" 에 사용된다.
 * 예: COMPLETED → GENERATING, REQUESTED → COMPLETED 등.</p>
 */
public class InvalidShowcaseModelStatusTransitionException extends CustomException {

    public InvalidShowcaseModelStatusTransitionException() {
        super(ErrorCode.SHOWCASE_MODEL_INVALID_STATUS_TRANSITION);
    }
}
