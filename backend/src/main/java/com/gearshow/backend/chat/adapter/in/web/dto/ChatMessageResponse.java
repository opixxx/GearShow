package com.gearshow.backend.chat.adapter.in.web.dto;

import com.gearshow.backend.chat.application.dto.ChatMessageResult;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;

import java.time.Instant;

/**
 * 메시지 행 응답 (api-spec §8-4).
 */
public record ChatMessageResponse(
        Long chatMessageId,
        Long senderId,
        long seq,
        ChatMessageType messageType,
        String content,
        String payloadJson,
        ChatMessageStatus messageStatus,
        Instant sentAt
) {

    public static ChatMessageResponse from(ChatMessageResult r) {
        return new ChatMessageResponse(
                r.chatMessageId(),
                r.senderId(),
                r.seq(),
                r.messageType(),
                r.content(),
                r.payloadJson(),
                r.status(),
                r.sentAt());
    }
}
