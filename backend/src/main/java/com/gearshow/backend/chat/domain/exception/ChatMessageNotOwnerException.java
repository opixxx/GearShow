package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 본인이 보내지 않은 메시지를 삭제하려 할 때 발생하는 예외.
 */
public class ChatMessageNotOwnerException extends CustomException {

    public ChatMessageNotOwnerException() {
        super(ErrorCode.CHAT_MESSAGE_NOT_OWNER);
    }
}
