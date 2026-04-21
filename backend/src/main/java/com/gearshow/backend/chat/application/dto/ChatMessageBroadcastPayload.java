package com.gearshow.backend.chat.application.dto;

import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;

import java.time.Instant;

/**
 * 채팅 메시지 브로드캐스트용 경량 DTO.
 *
 * <p>REST 응답이나 WebSocket 프레임 구성 시 모두 이 payload 를 공통으로 사용해
 * 필드 추가 · 직렬화 포맷 변경 시 한 곳만 수정하면 된다. 도메인 모델({@link ChatMessage})
 * 을 직접 노출하지 않는다.</p>
 */
public record ChatMessageBroadcastPayload(
        Long chatMessageId,
        Long chatRoomId,
        Long senderId,
        long seq,
        ChatMessageType messageType,
        String content,
        String payloadJson,
        Instant sentAt
) {

    /**
     * 저장된 도메인 메시지로부터 브로드캐스트 payload 를 생성한다.
     *
     * @param saved 저장이 완료된 {@link ChatMessage} (id · seq · sentAt 필수 채워진 상태)
     */
    public static ChatMessageBroadcastPayload from(ChatMessage saved) {
        return new ChatMessageBroadcastPayload(
                saved.getId(),
                saved.getChatRoomId(),
                saved.getSenderId(),
                saved.getSeq(),
                saved.getMessageType(),
                saved.getContent(),
                null,
                saved.getSentAt()
        );
    }
}
