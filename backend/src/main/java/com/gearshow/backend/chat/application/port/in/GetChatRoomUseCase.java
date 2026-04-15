package com.gearshow.backend.chat.application.port.in;

import com.gearshow.backend.chat.application.dto.ChatRoomDetailResult;

/**
 * 채팅방 상세 조회 유스케이스 (api-spec §8-2).
 */
public interface GetChatRoomUseCase {

    /**
     * @param chatRoomId  채팅방 ID
     * @param requesterId 요청 유저 ID (참여자 검증 대상)
     */
    ChatRoomDetailResult get(Long chatRoomId, Long requesterId);
}
