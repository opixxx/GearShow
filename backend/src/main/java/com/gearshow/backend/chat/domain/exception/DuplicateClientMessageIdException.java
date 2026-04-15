package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Instant;

/**
 * 동일한 {@code clientMessageId}로 재전송 요청이 왔을 때 발생하는 예외.
 *
 * <p>api-spec §8-5 규약: 409 응답과 함께 기존에 저장된 메시지 정보(id/seq/sentAt)를
 * 클라이언트에 반환해 재시도를 수렴시킨다. 이를 위해 기존 메시지 식별자를 함께 실어둔다.</p>
 */
@Getter
public class DuplicateClientMessageIdException extends CustomException {

    private final Long existingMessageId;
    private final Long existingSeq;
    private final Instant existingSentAt;

    public DuplicateClientMessageIdException(Long existingMessageId,
                                             Long existingSeq,
                                             Instant existingSentAt) {
        super(ErrorCode.DUPLICATE_CLIENT_MESSAGE_ID);
        this.existingMessageId = existingMessageId;
        this.existingSeq = existingSeq;
        this.existingSentAt = existingSentAt;
    }
}
