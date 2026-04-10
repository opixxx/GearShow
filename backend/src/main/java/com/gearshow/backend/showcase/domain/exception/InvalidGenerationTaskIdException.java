package com.gearshow.backend.showcase.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델 생성 task_id 값이 유효하지 않을 때 발생하는 도메인 예외.
 *
 * <p>Worker 가 Tripo createTask 직후 받은 task_id 가 null/blank 인 경우처럼
 * "도메인 불변식 위반" 을 표현한다.</p>
 */
public class InvalidGenerationTaskIdException extends CustomException {

    public InvalidGenerationTaskIdException() {
        super(ErrorCode.SHOWCASE_MODEL_INVALID_TASK_ID);
    }
}
