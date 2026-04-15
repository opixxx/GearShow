package com.gearshow.backend.chat.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 채팅방 읽음 마커 JPA 엔티티.
 *
 * <p>유니크: {@code (chat_room_id, user_id)}. 사용자별·채팅방별 1행.</p>
 */
@Entity
@Table(
        name = "chat_read_marker",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_read_marker_room_user",
                        columnNames = {"chat_room_id", "user_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatReadMarkerJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_read_marker_id")
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private ChatReadMarkerJpaEntity(Long id, Long chatRoomId, Long userId,
                                    Long lastReadMessageId, Instant updatedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = updatedAt;
    }

    /**
     * 읽음 메시지 ID를 갱신한다 (row lock 후 호출되는 전제).
     */
    public void updateLastReadMessageId(Long lastReadMessageId, Instant now) {
        this.lastReadMessageId = lastReadMessageId;
        this.updatedAt = now;
    }
}
