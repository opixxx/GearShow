package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.common.exception.ErrorCode;
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
 *
 * <p>4 책임(경로 매칭·인증 확인·채팅방 조회·참여자 검증)을 private 메서드로 분리해
 * SRP 를 유지한다. 예외 메시지는 전부 {@link ErrorCode} 경유.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    /** 채팅방 토픽 패턴. {@code \d{1,18}} 로 Long overflow 를 정규식 단계에서 차단. */
    private static final Pattern CHAT_ROOM_TOPIC_PATTERN =
            Pattern.compile("^/topic/chat-rooms/(\\d{1,18})$");

    private final ChatRoomPort chatRoomPort;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        Long chatRoomId = parseChatRoomId(accessor.getDestination());
        Long userId = requireAuthenticatedUserId(accessor);
        validateParticipant(chatRoomId, userId);

        log.debug("WebSocket 구독 허용: userId={}, chatRoomId={}", userId, chatRoomId);
        return message;
    }

    /** 목적지 경로에서 채팅방 ID 를 파싱한다. null·형식 불일치·overflow 모두 거부. */
    private Long parseChatRoomId(String destination) {
        if (destination == null) {
            throw fail(ErrorCode.CHAT_WS_INVALID_DESTINATION);
        }
        Matcher matcher = CHAT_ROOM_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            throw fail(ErrorCode.CHAT_WS_INVALID_DESTINATION);
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            // 정규식이 18자리로 제한하므로 이론상 도달 불가. 방어적 변환.
            throw fail(ErrorCode.CHAT_WS_INVALID_DESTINATION);
        }
    }

    /** STOMP Principal 에서 userId 를 꺼낸다. 미인증·타입 불일치 모두 거부. */
    private Long requireAuthenticatedUserId(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal stompPrincipal)) {
            throw fail(ErrorCode.CHAT_WS_UNAUTHENTICATED_SESSION);
        }
        return stompPrincipal.userId();
    }

    /** 채팅방 존재·참여자 여부를 검증한다. */
    private void validateParticipant(Long chatRoomId, Long userId) {
        ChatRoom room = chatRoomPort.findById(chatRoomId)
                .orElseThrow(() -> fail(ErrorCode.CHAT_WS_ROOM_NOT_FOUND));
        room.validateParticipant(userId);
    }

    private MessageDeliveryException fail(ErrorCode errorCode) {
        return new MessageDeliveryException(errorCode.getMessage());
    }
}
