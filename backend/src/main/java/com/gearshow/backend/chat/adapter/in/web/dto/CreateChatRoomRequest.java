package com.gearshow.backend.chat.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 채팅방 생성-또는-조회 요청 (api-spec §8-3).
 */
public record CreateChatRoomRequest(
        @NotNull(message = "showcaseId는 필수입니다")
        Long showcaseId
) {
}
