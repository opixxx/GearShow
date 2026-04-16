package com.gearshow.backend.chat.application.port.in;

import com.gearshow.backend.chat.application.dto.CreateOrGetChatRoomResult;

/**
 * 쇼케이스 "채팅하기" 버튼 진입점 유스케이스 (api-spec §8-3).
 *
 * <p>기존 채팅방이 있으면 반환, 없으면 새로 생성한다. idempotent.</p>
 */
public interface CreateOrGetChatRoomUseCase {

    /**
     * @param showcaseId 대상 쇼케이스 ID
     * @param buyerId    구매자(로그인 유저) ID
     */
    CreateOrGetChatRoomResult createOrGet(Long showcaseId, Long buyerId);
}
