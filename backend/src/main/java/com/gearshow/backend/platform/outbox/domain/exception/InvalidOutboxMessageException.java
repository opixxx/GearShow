package com.gearshow.backend.platform.outbox.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * Outbox 메시지 도메인 불변식을 위반했을 때 발생하는 예외.
 *
 * <p>필수 필드(aggregateType, aggregateId, eventType, topic, messageId, payload)
 * 가 누락된 상태로 {@code OutboxMessage.create(...)} 가 호출되면 던져진다.</p>
 */
public class InvalidOutboxMessageException extends CustomException {

    public InvalidOutboxMessageException() {
        super(ErrorCode.OUTBOX_INVALID_MESSAGE);
    }
}
