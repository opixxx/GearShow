package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 메시지가 허용 길이(2,000자)를 초과했을 때 발생하는 예외.
 */
public class ChatMessageTooLongException extends CustomException {

    public ChatMessageTooLongException() {
        super(ErrorCode.CHAT_MESSAGE_TOO_LONG);
    }
}
