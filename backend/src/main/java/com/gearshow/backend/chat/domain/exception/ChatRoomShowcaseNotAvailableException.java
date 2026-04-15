package com.gearshow.backend.chat.domain.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 쇼케이스 상태가 DELETED/SOLD 등으로 채팅을 시작할 수 없는 상태일 때 발생하는 예외.
 */
public class ChatRoomShowcaseNotAvailableException extends CustomException {

    public ChatRoomShowcaseNotAvailableException() {
        super(ErrorCode.CHAT_ROOM_SHOWCASE_NOT_AVAILABLE);
    }
}
