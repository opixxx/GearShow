package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.model.ChatMessage;
import org.springframework.stereotype.Component;

/**
 * {@link ChatMessage} 도메인 모델과 JPA 엔티티 간 변환 매퍼.
 */
@Component
public class ChatMessageMapper {

    public ChatMessageJpaEntity toJpaEntity(ChatMessage message) {
        return ChatMessageJpaEntity.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoomId())
                .senderId(message.getSenderId())
                .seq(message.getSeq())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .payloadJson(message.getPayloadJson())
                .clientMessageId(message.getClientMessageId())
                .status(message.getStatus())
                .sentAt(message.getSentAt())
                .build();
    }

    public ChatMessage toDomain(ChatMessageJpaEntity entity) {
        return ChatMessage.builder()
                .id(entity.getId())
                .chatRoomId(entity.getChatRoomId())
                .senderId(entity.getSenderId())
                .seq(entity.getSeq())
                .messageType(entity.getMessageType())
                .content(entity.getContent())
                .payloadJson(entity.getPayloadJson())
                .clientMessageId(entity.getClientMessageId())
                .status(entity.getStatus())
                .sentAt(entity.getSentAt())
                .build();
    }
}
