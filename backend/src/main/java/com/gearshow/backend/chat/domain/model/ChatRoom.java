package com.gearshow.backend.chat.domain.model;

import com.gearshow.backend.chat.domain.exception.ChatRoomClosedException;
import com.gearshow.backend.chat.domain.exception.ChatRoomOwnShowcaseException;
import com.gearshow.backend.chat.domain.exception.ForbiddenChatRoomAccessException;
import com.gearshow.backend.chat.domain.exception.InvalidChatRoomException;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 채팅방 도메인 엔티티 (Aggregate Root).
 *
 * <p>쇼케이스 단위 + 판매자-구매자 쌍으로 존재하는 1:1 채팅방.
 * {@code (showcaseId, buyerId)}가 유니크 키이며 판매자는 쇼케이스 소유자로 고정된다.</p>
 */
@Getter
public class ChatRoom {

    private final Long id;
    private final Long showcaseId;
    private final Long sellerId;
    private final Long buyerId;
    private final ChatRoomStatus status;
    private final Instant createdAt;
    private final Instant lastMessageAt;

    @Builder
    private ChatRoom(Long id, Long showcaseId, Long sellerId, Long buyerId,
                     ChatRoomStatus status, Instant createdAt, Instant lastMessageAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.status = status;
        this.createdAt = createdAt;
        this.lastMessageAt = lastMessageAt;
    }

    /**
     * 새로운 채팅방을 개설한다.
     *
     * @param showcaseId 대상 쇼케이스 ID
     * @param sellerId   판매자(쇼케이스 소유자) ID
     * @param buyerId    구매자 ID
     * @return ACTIVE 상태의 채팅방
     * @throws ChatRoomOwnShowcaseException seller와 buyer가 동일한 경우
     * @throws InvalidChatRoomException     필수 값이 비어 있는 경우
     */
    public static ChatRoom open(Long showcaseId, Long sellerId, Long buyerId) {
        if (showcaseId == null || sellerId == null || buyerId == null) {
            throw new InvalidChatRoomException();
        }
        if (sellerId.equals(buyerId)) {
            throw new ChatRoomOwnShowcaseException();
        }
        Instant now = Instant.now();
        return ChatRoom.builder()
                .showcaseId(showcaseId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .status(ChatRoomStatus.ACTIVE)
                .createdAt(now)
                // 메시지 미발송 상태의 기본 정렬 기준 — DB 인덱스 활용 및 COALESCE 제거.
                .lastMessageAt(now)
                .build();
    }

    /**
     * 해당 사용자가 참여자(판매자 또는 구매자)인지 검증한다.
     *
     * @param userId 검증할 사용자 ID
     * @throws ForbiddenChatRoomAccessException 참여자가 아닌 경우
     */
    public void validateParticipant(Long userId) {
        if (userId == null || (!userId.equals(sellerId) && !userId.equals(buyerId))) {
            throw new ForbiddenChatRoomAccessException();
        }
    }

    /**
     * 상대방(peer)의 사용자 ID를 반환한다. 참여자가 아닌 경우 예외 발생.
     *
     * @param userId 기준 사용자 ID
     * @return 상대방 사용자 ID
     */
    public Long peerOf(Long userId) {
        validateParticipant(userId);
        return userId.equals(sellerId) ? buyerId : sellerId;
    }

    /**
     * 현재 채팅방이 메시지 송신 가능한 상태인지 검증한다.
     *
     * @throws ChatRoomClosedException CLOSED 상태인 경우
     */
    public void validateSendable() {
        if (status != ChatRoomStatus.ACTIVE) {
            throw new ChatRoomClosedException();
        }
    }

    /**
     * 메시지 발송 시점에 {@code lastMessageAt}을 최신값으로 갱신한다.
     *
     * @param sentAt 기준 시각
     * @return 갱신된 채팅방
     */
    public ChatRoom touch(Instant sentAt) {
        return toBuilder()
                .lastMessageAt(sentAt)
                .build();
    }

    /**
     * 채팅방을 종료 상태로 전환한다. 이후 신규 메시지 송신 불가.
     *
     * @return CLOSED 상태 채팅방
     */
    public ChatRoom close() {
        if (status == ChatRoomStatus.CLOSED) {
            return this;
        }
        return toBuilder()
                .status(ChatRoomStatus.CLOSED)
                .build();
    }

    private ChatRoomBuilder toBuilder() {
        return ChatRoom.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .sellerId(this.sellerId)
                .buyerId(this.buyerId)
                .status(this.status)
                .createdAt(this.createdAt)
                .lastMessageAt(this.lastMessageAt);
    }
}
