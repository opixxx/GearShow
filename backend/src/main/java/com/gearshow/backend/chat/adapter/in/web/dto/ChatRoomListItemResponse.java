package com.gearshow.backend.chat.adapter.in.web.dto;

import com.gearshow.backend.chat.application.dto.ChatRoomListItemResult;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;

import java.time.Instant;

/**
 * 채팅방 목록 행 응답 (api-spec §8-1).
 */
public record ChatRoomListItemResponse(
        Long chatRoomId,
        Long showcaseId,
        String showcaseTitle,
        String showcaseThumbnailUrl,
        PeerResponse peer,
        LastMessageResponse lastMessage,
        long unreadCount,
        ChatRoomStatus chatRoomStatus
) {

    public record PeerResponse(Long userId, String nickname, String profileImageUrl) {
    }

    public record LastMessageResponse(String content, ChatMessageType messageType, Instant sentAt) {
    }

    public static ChatRoomListItemResponse from(ChatRoomListItemResult r) {
        return new ChatRoomListItemResponse(
                r.chatRoomId(),
                r.showcaseId(),
                r.showcaseTitle(),
                r.showcaseThumbnailUrl(),
                new PeerResponse(r.peer().userId(), r.peer().nickname(), r.peer().profileImageUrl()),
                r.lastMessage() == null
                        ? null
                        : new LastMessageResponse(
                                r.lastMessage().content(),
                                r.lastMessage().messageType(),
                                r.lastMessage().sentAt()),
                r.unreadCount(),
                r.status());
    }
}
