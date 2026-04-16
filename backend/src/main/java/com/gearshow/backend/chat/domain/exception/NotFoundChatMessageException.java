package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 채팅 메시지를 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundChatMessageException extends CustomException {

    public NotFoundChatMessageException() {
        super(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }
}
