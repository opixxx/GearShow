package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 종료된(CLOSED) 채팅방에 메시지를 보내려 할 때 발생하는 예외.
 */
public class ChatRoomClosedException extends CustomException {

    public ChatRoomClosedException() {
        super(ErrorCode.CHAT_ROOM_CLOSED);
    }
}
