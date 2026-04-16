package com.gearshow.backend.chat.application.dto;

import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;

import java.time.Instant;

/**
 * 채팅방 상세 조회 결과 DTO (api-spec §8-2).
 */
public record ChatRoomDetailResult(
        Long chatRoomId,
        Long showcaseId,
        ChatParticipant seller,
        ChatParticipant buyer,
        ChatRoomStatus status,
        Instant createdAt,
        Instant lastMessageAt
) {

    public record ChatParticipant(Long userId, String nickname, String profileImageUrl) {
    }
}
