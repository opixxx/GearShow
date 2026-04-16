package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.domain.model.ChatReadMarker;

import java.util.Optional;

/**
 * 채팅방 읽음 마커 Outbound Port.
 */
public interface ChatReadMarkerPort {

    Optional<ChatReadMarker> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    /**
     * {@code (chatRoomId, userId)} 기준으로 upsert 한다.
     * 기존 마커가 있으면 row lock 후 {@link ChatReadMarker#updateTo(Long)}로 갱신,
     * 없으면 신규 저장한다. 반환값은 저장 후 상태.
     */
    ChatReadMarker upsert(Long chatRoomId, Long userId, Long lastReadMessageId);
}
