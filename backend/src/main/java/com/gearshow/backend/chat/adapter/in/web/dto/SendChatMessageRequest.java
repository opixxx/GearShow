package com.gearshow.backend.chat.adapter.in.web.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 메시지 송신 요청 (api-spec §8-5).
 */
public record SendChatMessageRequest(
        @NotNull(message = "messageType은 필수입니다")
        ChatMessageType messageType,

        @NotBlank(message = "content는 비어 있을 수 없습니다")
        @Size(max = 2_000, message = "메시지는 2,000자 이하여야 합니다")
        String content,

        @Size(max = 64, message = "clientMessageId는 64자 이하여야 합니다")
        String clientMessageId
) {
}
