package com.gearshow.backend.chat.adapter.in.websocket.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;

import java.time.Instant;

/**
 * STOMP 서버 → 클라이언트 메시지 응답 (api-spec §8-6 구독 메시지 스키마).
 */
public record StompChatMessageResponse(
        String type,
        Payload payload
) {

    public record Payload(
            Long chatMessageId,
            Long chatRoomId,
            Long senderId,
            long seq,
            ChatMessageType messageType,
            String content,
            String payloadJson,
            Instant sentAt
    ) {
    }

    public static StompChatMessageResponse of(
            Long chatMessageId, Long chatRoomId, Long senderId,
            long seq, ChatMessageType messageType, String content,
            String payloadJson, Instant sentAt) {
        return new StompChatMessageResponse(
                "MESSAGE",
                new Payload(chatMessageId, chatRoomId, senderId, seq, messageType, content, payloadJson, sentAt)
        );
    }
}
