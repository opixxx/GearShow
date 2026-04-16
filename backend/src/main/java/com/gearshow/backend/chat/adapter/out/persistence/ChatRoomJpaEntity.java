package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
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
 * 채팅방 JPA 엔티티.
 *
 * <p>유니크 키: {@code (showcase_id, buyer_id)}.
 * 인덱스: 참여자 기준 조회용 {@code (seller_id)}, {@code (buyer_id)}.</p>
 */
@Entity
@Table(
        name = "chat_room",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_room_showcase_buyer",
                        columnNames = {"showcase_id", "buyer_id"})
        },
        indexes = {
                // 참여자 기준 목록 정렬용 복합 인덱스 — filesort 회피.
                // 정렬 기준이 last_message_at DESC인데 MySQL 8은 backward index scan을 지원한다.
                @Index(name = "ix_chat_room_seller_activity",
                        columnList = "seller_id, last_message_at, chat_room_id"),
                @Index(name = "ix_chat_room_buyer_activity",
                        columnList = "buyer_id, last_message_at, chat_room_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false)
    private Long showcaseId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_room_status", nullable = false, length = 16)
    private ChatRoomStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 마지막 활동 시각.
     *
     * <p>DB 정렬 인덱스가 직접 활용될 수 있도록 NOT NULL을 강제한다.
     * 메시지 미발송 상태에서도 {@code createdAt} 값을 기본으로 채워두고,
     * 실제 메시지가 없는지 여부는 {@code ChatRoomListProjection.lastMessageId == null}로 구분한다.</p>
     */
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Builder
    private ChatRoomJpaEntity(Long id, Long showcaseId, Long sellerId, Long buyerId,
                              ChatRoomStatus status, Instant createdAt, Instant lastMessageAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.status = status;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
    }
}
