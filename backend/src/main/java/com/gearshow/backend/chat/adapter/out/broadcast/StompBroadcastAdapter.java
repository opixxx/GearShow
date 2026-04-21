package com.gearshow.backend.chat.adapter.out.broadcast;

import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageResponse;
import com.gearshow.backend.chat.application.dto.ChatMessageBroadcastPayload;
import com.gearshow.backend.chat.application.port.out.ChatMessageBroadcastPort;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * STOMP 토픽 기반 브로드캐스트 어댑터.
 *
 * <p>채팅방 토픽({@code /topic/chat-rooms/{chatRoomId}}) 으로 {@link StompChatMessageResponse}
 * 프레임을 전송한다. 이 프로젝트에서 {@link SimpMessagingTemplate} 직접 의존은 이 어댑터 하나로
 * 국한된다 (ADR-009).</p>
 */
@Component
@RequiredArgsConstructor
public class StompBroadcastAdapter implements ChatMessageBroadcastPort {

    private static final String TOPIC_CHAT_ROOM_PREFIX = "/topic/chat-rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publish(ChatMessageBroadcastPayload payload) {
        messagingTemplate.convertAndSend(
                TOPIC_CHAT_ROOM_PREFIX + payload.chatRoomId(),
                StompChatMessageResponse.from(payload));
    }
}
