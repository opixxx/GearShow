package com.gearshow.backend.chat.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 읽음 처리 요청 (api-spec §8-7).
 */
public record MarkReadRequest(
        @NotNull(message = "lastReadMessageId는 필수입니다")
        Long lastReadMessageId
) {
}
