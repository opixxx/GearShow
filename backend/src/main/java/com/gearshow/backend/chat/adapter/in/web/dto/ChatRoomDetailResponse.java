package com.gearshow.backend.chat.adapter.in.web.dto;

import com.gearshow.backend.chat.application.dto.ChatRoomDetailResult;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;

import java.time.Instant;

/**
 * 채팅방 상세 응답 (api-spec §8-2).
 */
public record ChatRoomDetailResponse(
        Long chatRoomId,
        Long showcaseId,
        ParticipantResponse seller,
        ParticipantResponse buyer,
        ChatRoomStatus chatRoomStatus,
        Instant createdAt,
        Instant lastMessageAt
) {

    public record ParticipantResponse(Long userId, String nickname, String profileImageUrl) {
    }

    public static ChatRoomDetailResponse from(ChatRoomDetailResult r) {
        return new ChatRoomDetailResponse(
                r.chatRoomId(),
                r.showcaseId(),
                new ParticipantResponse(r.seller().userId(), r.seller().nickname(), r.seller().profileImageUrl()),
                new ParticipantResponse(r.buyer().userId(), r.buyer().nickname(), r.buyer().profileImageUrl()),
                r.status(),
                r.createdAt(),
                r.lastMessageAt());
    }
}
