package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 채팅 메시지 생성 시 불변식 위반(빈 본문·시스템 타입에 sender 존재 등)에서 발생하는 예외.
 */
public class InvalidChatMessageException extends CustomException {

    public InvalidChatMessageException() {
        super(ErrorCode.CHAT_MESSAGE_INVALID);
    }
}
