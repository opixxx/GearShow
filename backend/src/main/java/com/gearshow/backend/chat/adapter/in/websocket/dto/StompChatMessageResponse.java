package com.gearshow.backend.chat.adapter.in.websocket.dto;

import com.gearshow.backend.chat.application.dto.ChatMessageBroadcastPayload;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;

import java.time.Instant;

/**
 * STOMP 서버 → 클라이언트 메시지 응답 (api-spec §8-6 구독 메시지 스키마).
 *
 * <p>브로드캐스트 진입점이 {@link ChatMessageBroadcastPayload} 로 통일되었으므로 {@link #from(ChatMessageBroadcastPayload)}
 * 팩토리만 사용한다. 8-params {@code of(...)} 는 인자 순서 실수 위험 때문에 제거되었다.</p>
 */
public record StompChatMessageResponse(
        String type,
        Payload payload
) {

    private static final String TYPE_MESSAGE = "MESSAGE";

    public record Payload(
            Long chatMessageId,
            Long chatRoomId,
            Long senderId,
            long seq,
            ChatMessageType messageType,
            String content,
            String payloadJson,
            Instant sentAt
    ) {
    }

    /**
     * 브로드캐스트 payload 로부터 STOMP 응답 프레임을 생성한다.
     */
    public static StompChatMessageResponse from(ChatMessageBroadcastPayload p) {
        return new StompChatMessageResponse(
                TYPE_MESSAGE,
                new Payload(
                        p.chatMessageId(), p.chatRoomId(), p.senderId(), p.seq(),
                        p.messageType(), p.content(), p.payloadJson(), p.sentAt()
                )
        );
    }
}
