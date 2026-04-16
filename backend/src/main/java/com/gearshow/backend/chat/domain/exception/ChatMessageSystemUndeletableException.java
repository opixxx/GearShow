package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 시스템 메시지(senderId가 NULL인 SYSTEM_* 타입)를 삭제하려 할 때 발생하는 예외.
 */
public class ChatMessageSystemUndeletableException extends CustomException {

    public ChatMessageSystemUndeletableException() {
        super(ErrorCode.CHAT_MESSAGE_SYSTEM_UNDELETABLE);
    }
}
