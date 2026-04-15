package com.gearshow.backend.chat.application.dto;

import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;

import java.time.Instant;

/**
 * 채팅 메시지 조회 결과 DTO.
 */
public record ChatMessageResult(
        Long chatMessageId,
        Long senderId,
        long seq,
        ChatMessageType messageType,
        String content,
        String payloadJson,
        ChatMessageStatus status,
        Instant sentAt
) {

    public static ChatMessageResult from(ChatMessage message) {
        return new ChatMessageResult(
                message.getId(),
                message.getSenderId(),
                message.getSeq(),
                message.getMessageType(),
                message.getContent(),
                message.getPayloadJson(),
                message.getStatus(),
                message.getSentAt()
        );
    }
}
