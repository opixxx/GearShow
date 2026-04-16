package com.gearshow.backend.chat.application.port.in;

/**
 * 읽음 처리 유스케이스 (api-spec §8-7).
 *
 * <p>채팅방 진입 시점에 호출되어 {@code CHAT_READ_MARKER.lastReadMessageId}를 갱신한다.</p>
 */
public interface MarkChatRoomReadUseCase {

    /**
     * @param chatRoomId        채팅방 ID
     * @param requesterId       요청 유저 ID
     * @param lastReadMessageId 읽음으로 표시할 마지막 메시지 ID
     */
    void mark(Long chatRoomId, Long requesterId, Long lastReadMessageId);
}
