package com.gearshow.backend.platform.outbox.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Outbox 이벤트 페이로드를 JSON 으로 직렬화하는 도중 실패했을 때 발생하는 예외.
 *
 * <p>이는 대부분 개발 시점 버그(도메인 DTO 에 직렬화 불가능한 필드 포함)로 간주되므로,
 * 원인을 보존하여 로그에서 추적 가능하게 한다.</p>
 */
public class OutboxEventSerializationException extends CustomException {

    public OutboxEventSerializationException(Throwable cause) {
        super(ErrorCode.OUTBOX_EVENT_SERIALIZATION_FAILED, cause);
    }
}
