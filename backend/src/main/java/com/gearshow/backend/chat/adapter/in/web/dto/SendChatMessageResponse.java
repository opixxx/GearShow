package com.gearshow.backend.chat.adapter.in.web.dto;

import com.gearshow.backend.chat.application.dto.SendChatMessageResult;

import java.time.Instant;

/**
 * 메시지 송신 결과 응답 (api-spec §8-5).
 */
public record SendChatMessageResponse(Long chatMessageId, long seq, Instant sentAt) {

    public static SendChatMessageResponse from(SendChatMessageResult r) {
        return new SendChatMessageResponse(r.chatMessageId(), r.seq(), r.sentAt());
    }
}
