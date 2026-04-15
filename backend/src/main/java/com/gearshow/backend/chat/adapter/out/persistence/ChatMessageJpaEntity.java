package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 채팅 메시지 JPA 엔티티.
 *
 * <p>인덱스: {@code (chat_room_id, seq)} — 순서 조회,
 * {@code (chat_room_id, sent_at DESC)}는 MySQL DESC 인덱스 지원이 제한적이어서
 * {@code (chat_room_id, chat_message_id)} 로 대체 (ID 단조 증가 보장).</p>
 *
 * <p>유니크 제약: {@code (chat_room_id, sender_id, client_message_id)} — 멱등성.</p>
 */
@Entity
@Table(
        name = "chat_message",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_message_client_msg_id",
                        columnNames = {"chat_room_id", "sender_id", "client_message_id"})
        },
        indexes = {
                @Index(name = "ix_chat_message_room_seq",
                        columnList = "chat_room_id, seq"),
                @Index(name = "ix_chat_message_room_id_desc",
                        columnList = "chat_room_id, chat_message_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id")
    private Long senderId;

    @Column(name = "seq", nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 48)
    private ChatMessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "payload_json", columnDefinition = "json")
    private String payloadJson;

    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_status", nullable = false, length = 16)
    private ChatMessageStatus status;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Builder
    private ChatMessageJpaEntity(Long id, Long chatRoomId, Long senderId, long seq,
                                 ChatMessageType messageType, String content, String payloadJson,
                                 String clientMessageId, ChatMessageStatus status, Instant sentAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.seq = seq;
        this.messageType = messageType;
        this.content = content;
        this.payloadJson = payloadJson;
        this.clientMessageId = clientMessageId;
        this.status = status;
        this.sentAt = sentAt;
    }
}
