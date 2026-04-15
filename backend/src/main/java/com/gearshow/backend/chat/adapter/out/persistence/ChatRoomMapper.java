package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.model.ChatRoom;
import org.springframework.stereotype.Component;

/**
 * {@link ChatRoom} 도메인 모델과 JPA 엔티티 간 변환 매퍼.
 */
@Component
public class ChatRoomMapper {

    public ChatRoomJpaEntity toJpaEntity(ChatRoom chatRoom) {
        return ChatRoomJpaEntity.builder()
                .id(chatRoom.getId())
                .showcaseId(chatRoom.getShowcaseId())
                .sellerId(chatRoom.getSellerId())
                .buyerId(chatRoom.getBuyerId())
                .status(chatRoom.getStatus())
                .createdAt(chatRoom.getCreatedAt())
                .lastMessageAt(chatRoom.getLastMessageAt())
                .build();
    }

    public ChatRoom toDomain(ChatRoomJpaEntity entity) {
        return ChatRoom.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .sellerId(entity.getSellerId())
                .buyerId(entity.getBuyerId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .lastMessageAt(entity.getLastMessageAt())
                .build();
    }
}
