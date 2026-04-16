package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 채팅방 참여자가 아닌 사용자가 접근하려 할 때 발생하는 예외.
 */
public class ForbiddenChatRoomAccessException extends CustomException {

    public ForbiddenChatRoomAccessException() {
        super(ErrorCode.FORBIDDEN_CHAT_ROOM_ACCESS);
    }
}
