package com.gearshow.backend.chat.adapter.in.websocket.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;

/**
 * STOMP 클라이언트 → 서버 메시지 요청 (api-spec §8-6 송신 스키마).
 */
public record StompChatMessageRequest(
        ChatMessageType messageType,
        String content,
        String clientMessageId
) {
}
