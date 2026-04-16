package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 채팅방을 찾을 수 없을 때 발생하는 예외.
 */
public class NotFoundChatRoomException extends CustomException {

    public NotFoundChatRoomException() {
        super(ErrorCode.CHAT_ROOM_NOT_FOUND);
    }
}
