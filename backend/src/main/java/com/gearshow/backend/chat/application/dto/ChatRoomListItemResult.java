package com.gearshow.backend.chat.application.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;

import java.time.Instant;

/**
 * 채팅방 목록 행 DTO (api-spec §8-1).
 *
 * <p>lastMessage는 채팅이 없으면 {@code null}.</p>
 */
public record ChatRoomListItemResult(
        Long chatRoomId,
        Long showcaseId,
        String showcaseTitle,
        String showcaseThumbnailUrl,
        Peer peer,
        LastMessage lastMessage,
        long unreadCount,
        ChatRoomStatus status,
        Instant lastActivityAt
) {

    public record Peer(Long userId, String nickname, String profileImageUrl) {
    }

    public record LastMessage(String content, ChatMessageType messageType, Instant sentAt) {
    }
}
