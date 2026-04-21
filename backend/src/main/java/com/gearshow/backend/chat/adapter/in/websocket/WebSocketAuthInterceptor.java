package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.application.port.out.VerifyJwtTokenPort;
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
import org.springframework.util.StringUtils;

/**
 * STOMP CONNECT 시 JWT 토큰을 검증하여 인증 주체를 세팅한다.
 *
 * <p>토큰 전달 방식: STOMP 네이티브 헤더 {@code Authorization: Bearer {token}}</p>
 *
 * <p>JWT 검증은 chat 의 아웃바운드 포트 {@link VerifyJwtTokenPort} 를 경유한다.
 * chat 컨텍스트가 user 인프라에 직접 의존하지 않도록 하기 위함 (ADR-008 참조).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final VerifyJwtTokenPort verifyJwtTokenPort;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        Long userId = verifyJwtTokenPort.resolveUserId(extractToken(accessor))
                .orElseThrow(() -> {
                    log.warn("WebSocket 인증 실패: 유효하지 않은 토큰");
                    return new MessageDeliveryException("인증에 실패했습니다. 유효한 JWT 토큰을 제공해주세요.");
                });

        accessor.setUser(new StompPrincipal(userId));
        log.debug("WebSocket 인증 성공: userId={}", userId);
        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
