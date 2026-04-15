package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.model.ChatReadMarker;
import org.springframework.stereotype.Component;

/**
 * {@link ChatReadMarker} 도메인 모델과 JPA 엔티티 간 변환 매퍼.
 */
@Component
public class ChatReadMarkerMapper {

    public ChatReadMarkerJpaEntity toJpaEntity(ChatReadMarker marker) {
        return ChatReadMarkerJpaEntity.builder()
                .id(marker.getId())
                .chatRoomId(marker.getChatRoomId())
                .userId(marker.getUserId())
                .lastReadMessageId(marker.getLastReadMessageId())
                .updatedAt(marker.getUpdatedAt())
                .build();
    }

    public ChatReadMarker toDomain(ChatReadMarkerJpaEntity entity) {
        return ChatReadMarker.builder()
                .id(entity.getId())
                .chatRoomId(entity.getChatRoomId())
                .userId(entity.getUserId())
                .lastReadMessageId(entity.getLastReadMessageId())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
