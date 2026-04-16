package com.gearshow.backend.chat.domain.model;

import com.gearshow.backend.chat.domain.exception.InvalidChatRoomException;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 채팅방 읽음 마커 도메인 엔티티.
 *
 * <p>CHAT_ROOM Aggregate에 종속된다. 각 사용자가 각 채팅방에서 마지막으로 읽은
 * 메시지 ID를 추적하며 {@code UNIQUE(chatRoomId, userId)} 제약을 가진다.</p>
 */
@Getter
public class ChatReadMarker {

    private final Long id;
    private final Long chatRoomId;
    private final Long userId;
    private final Long lastReadMessageId;
    private final Instant updatedAt;

    @Builder
    private ChatReadMarker(Long id, Long chatRoomId, Long userId,
                           Long lastReadMessageId, Instant updatedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 마커를 생성한다. 최초 진입 시 사용된다.
     */
    public static ChatReadMarker create(Long chatRoomId, Long userId, Long lastReadMessageId) {
        if (chatRoomId == null || userId == null) {
            throw new InvalidChatRoomException();
        }
        return ChatReadMarker.builder()
                .chatRoomId(chatRoomId)
                .userId(userId)
                .lastReadMessageId(lastReadMessageId)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * 읽음 메시지 ID를 갱신한다. 이미 더 앞선 값이면 그대로 둔다 (역진 방지).
     */
    public ChatReadMarker updateTo(Long nextLastReadMessageId) {
        if (nextLastReadMessageId == null) {
            return this;
        }
        if (lastReadMessageId != null && lastReadMessageId >= nextLastReadMessageId) {
            return this;
        }
        return ChatReadMarker.builder()
                .id(this.id)
                .chatRoomId(this.chatRoomId)
                .userId(this.userId)
                .lastReadMessageId(nextLastReadMessageId)
                .updatedAt(Instant.now())
                .build();
    }
}
