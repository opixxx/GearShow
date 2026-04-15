package com.gearshow.backend.chat.application.port.in;

import com.gearshow.backend.chat.application.dto.ChatMessageResult;
import com.gearshow.backend.common.dto.PageInfo;

/**
 * 채팅방 메시지 히스토리 조회 유스케이스 (api-spec §8-4).
 */
public interface ListChatMessagesUseCase {

    /**
     * 특정 메시지 ID 이전의 메시지를 시간순으로 조회한다.
     *
     * @param chatRoomId  채팅방 ID
     * @param requesterId 요청 유저 ID (참여자 검증 대상)
     * @param before      해당 메시지 ID 이전 조회 (null이면 최신부터)
     * @param size        페이지 크기 (1~200)
     */
    PageInfo<ChatMessageResult> list(Long chatRoomId, Long requesterId, Long before, int size);
}
