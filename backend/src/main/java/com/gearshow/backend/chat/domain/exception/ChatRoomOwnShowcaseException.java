package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 자신의 쇼케이스에 채팅방을 생성하려 할 때 발생하는 예외.
 */
public class ChatRoomOwnShowcaseException extends CustomException {

    public ChatRoomOwnShowcaseException() {
        super(ErrorCode.CHAT_ROOM_OWN_SHOWCASE);
    }
}
