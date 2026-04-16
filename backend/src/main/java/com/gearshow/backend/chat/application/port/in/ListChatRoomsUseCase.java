package com.gearshow.backend.chat.application.port.in;

import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult;
import com.gearshow.backend.common.dto.PageInfo;

/**
 * 참여 중인 채팅방 목록 조회 유스케이스 (api-spec §8-1).
 */
public interface ListChatRoomsUseCase {

    /**
     * 로그인 유저가 판매자 또는 구매자로 참여 중인 채팅방을 최신 활동 순으로 조회한다.
     *
     * @param userId    로그인 유저 ID
     * @param pageToken 커서 페이지 토큰 (nullable)
     * @param size      페이지 크기
     */
    PageInfo<ChatRoomListItemResult> list(Long userId, String pageToken, int size);
}
