package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP SUBSCRIBE 시 채팅방 참여자 검증을 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_ROOM_TOPIC_PATTERN =
            Pattern.compile("^/topic/chat-rooms/(\\d+)$");

    private final ChatRoomPort chatRoomPort;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = CHAT_ROOM_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw new MessageDeliveryException("허용되지 않는 구독 경로입니다: " + destination);
        }

        if (accessor.getUser() == null) {
            throw new MessageDeliveryException("인증되지 않은 사용자는 구독할 수 없습니다.");
        }

        Long chatRoomId = Long.parseLong(matcher.group(1));
        Long userId = ((StompPrincipal) accessor.getUser()).userId();

        ChatRoom room = chatRoomPort.findById(chatRoomId)
                .orElseThrow(() -> new MessageDeliveryException("채팅방을 찾을 수 없습니다: " + chatRoomId));
        room.validateParticipant(userId);

        log.debug("WebSocket 구독 허용: userId={}, chatRoomId={}", userId, chatRoomId);
        return message;
    }
}
