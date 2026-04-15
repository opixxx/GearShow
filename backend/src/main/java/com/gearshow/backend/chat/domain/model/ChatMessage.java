package com.gearshow.backend.chat.domain.model;

import com.gearshow.backend.chat.domain.exception.ChatMessageNotOwnerException;
import com.gearshow.backend.chat.domain.exception.ChatMessageSystemUndeletableException;
import com.gearshow.backend.chat.domain.exception.ChatMessageTooLongException;
import com.gearshow.backend.chat.domain.exception.InvalidChatMessageException;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 채팅 메시지 도메인 엔티티.
 *
 * <p>CHAT_ROOM Aggregate에 종속된다. 시스템 메시지는 {@code senderId}가 null이며,
 * soft delete 대상이 아니다. Phase 1 REST MVP는 {@link ChatMessageType#TEXT}만 사용자 송신을 허용한다.</p>
 */
@Getter
public class ChatMessage {

    /** 사용자 메시지 최대 길이 (api-spec §8-5). */
    public static final int MAX_CONTENT_LENGTH = 2_000;

    /**
     * soft delete된 메시지가 목록 조회 시 노출되는 플레이스홀더 (api-spec §8-8).
     * 단일 정의. 목록 응답·마지막 메시지 스냅샷 모두 이 값을 사용한다.
     */
    public static final String DELETED_PLACEHOLDER = "삭제된 메시지입니다";

    private final Long id;
    private final Long chatRoomId;
    private final Long senderId;
    private final long seq;
    private final ChatMessageType messageType;
    private final String content;
    private final String payloadJson;
    private final String clientMessageId;
    private final ChatMessageStatus status;
    private final Instant sentAt;

    @Builder
    private ChatMessage(Long id, Long chatRoomId, Long senderId, long seq,
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

    /**
     * 사용자가 입력한 텍스트 메시지를 생성한다.
     *
     * @param chatRoomId      채팅방 ID
     * @param senderId        발신자 ID (NOT NULL)
     * @param seq             채팅방 내 단조 증가 순번
     * @param content         본문 (빈 값 금지, 2,000자 이하)
     * @param clientMessageId 멱등성 키 (nullable)
     * @return ACTIVE 상태의 TEXT 메시지
     */
    public static ChatMessage text(Long chatRoomId, Long senderId, long seq,
                                   String content, String clientMessageId) {
        if (chatRoomId == null || senderId == null
                || content == null || content.isBlank()) {
            throw new InvalidChatMessageException();
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ChatMessageTooLongException();
        }
        return ChatMessage.builder()
                .chatRoomId(chatRoomId)
                .senderId(senderId)
                .seq(seq)
                .messageType(ChatMessageType.TEXT)
                .content(content)
                .payloadJson(null)
                .clientMessageId(clientMessageId)
                .status(ChatMessageStatus.ACTIVE)
                .sentAt(Instant.now())
                .build();
    }

    /**
     * 본인 메시지인지 검증한다.
     *
     * @param userId 기준 사용자 ID
     * @throws ChatMessageNotOwnerException 본인 메시지가 아닐 때
     */
    public void validateOwner(Long userId) {
        if (senderId == null || !senderId.equals(userId)) {
            throw new ChatMessageNotOwnerException();
        }
    }

    /**
     * 시스템 메시지가 아닌지 검증한다.
     *
     * @throws ChatMessageSystemUndeletableException 시스템 메시지인 경우
     */
    public void validateNotSystem() {
        if (messageType.isSystem()) {
            throw new ChatMessageSystemUndeletableException();
        }
    }

    /**
     * 이미 삭제된 메시지 여부.
     */
    public boolean isDeleted() {
        return status == ChatMessageStatus.DELETED;
    }

    /**
     * 본인 메시지를 soft delete 한다.
     *
     * @param requesterId 요청 사용자 ID
     * @return DELETED 상태 메시지
     */
    public ChatMessage softDelete(Long requesterId) {
        validateOwner(requesterId);
        validateNotSystem();
        if (isDeleted()) {
            return this;
        }
        return ChatMessage.builder()
                .id(this.id)
                .chatRoomId(this.chatRoomId)
                .senderId(this.senderId)
                .seq(this.seq)
                .messageType(this.messageType)
                .content(this.content)
                .payloadJson(this.payloadJson)
                .clientMessageId(this.clientMessageId)
                .status(ChatMessageStatus.DELETED)
                .sentAt(this.sentAt)
                .build();
    }
}
