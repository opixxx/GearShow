package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 채팅방 생성 시 필수 값 누락 등 불변식 위반에서 발생하는 예외.
 */
public class InvalidChatRoomException extends CustomException {

    public InvalidChatRoomException() {
        super(ErrorCode.CHAT_ROOM_INVALID);
    }
}
