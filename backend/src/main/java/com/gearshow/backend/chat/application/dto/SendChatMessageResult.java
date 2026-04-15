package com.gearshow.backend.chat.application.dto;

import java.time.Instant;

/**
 * 메시지 송신 결과 DTO (api-spec §8-5).
 */
public record SendChatMessageResult(Long chatMessageId, long seq, Instant sentAt) {
}
