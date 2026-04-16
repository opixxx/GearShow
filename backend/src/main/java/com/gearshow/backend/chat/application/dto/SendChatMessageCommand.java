package com.gearshow.backend.chat.application.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;

/**
 * 메시지 송신 커맨드 (api-spec §8-5).
 *
 * @param chatRoomId      채팅방 ID
 * @param senderId        인증된 발신자 ID (컨트롤러가 세션에서 주입)
 * @param messageType     메시지 타입 (Phase 1은 TEXT만 허용)
 * @param content         본문
 * @param clientMessageId 멱등성 키 (nullable)
 */
public record SendChatMessageCommand(
        Long chatRoomId,
        Long senderId,
        ChatMessageType messageType,
        String content,
        String clientMessageId
) {
}
