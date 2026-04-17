package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.user.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private Message<?> createMutableStompMessage(StompCommand command, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("유효한 JWT로 CONNECT 시 StompPrincipal이 세팅된다")
    void connectWithValidToken() {
        String token = "valid-token";
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUserId(token)).willReturn(42L);

        Message<?> result = interceptor.preSend(
                createMutableStompMessage(StompCommand.CONNECT, "Bearer " + token), null);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("42");
    }

    @Test
    @DisplayName("만료된 JWT로 CONNECT 시 예외가 발생한다")
    void connectWithExpiredToken() {
        String token = "expired-token";
        given(jwtTokenProvider.validateToken(token)).willReturn(false);

        Message<?> message = createMutableStompMessage(StompCommand.CONNECT, "Bearer " + token);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("토큰 없이 CONNECT 시 예외가 발생한다")
    void connectWithoutToken() {
        Message<?> message = createMutableStompMessage(StompCommand.CONNECT, null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("CONNECT가 아닌 명령은 인터셉터를 통과한다")
    void nonConnectCommandPassesThrough() {
        Message<?> message = createMutableStompMessage(StompCommand.SEND, null);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }
}
