package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionInterceptorTest {

    @InjectMocks
    private WebSocketSubscriptionInterceptor interceptor;

    @Mock
    private ChatRoomPort chatRoomPort;

    @Test
    @DisplayName("채팅방 참여자는 구독에 성공한다")
    void participantCanSubscribe() {
        Long chatRoomId = 1L;
        Long userId = 10L;
        ChatRoom room = ChatRoom.builder()
                .id(chatRoomId).showcaseId(100L).sellerId(userId).buyerId(20L)
                .status(ChatRoomStatus.ACTIVE).build();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/chat-rooms/" + chatRoomId);
        accessor.setUser(new StompPrincipal(userId));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(chatRoomPort.findById(chatRoomId)).willReturn(Optional.of(room));

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("비참여자는 구독이 거부된다")
    void nonParticipantIsRejected() {
        Long chatRoomId = 1L;
        Long nonParticipantId = 999L;
        ChatRoom room = ChatRoom.builder()
                .id(chatRoomId).showcaseId(100L).sellerId(10L).buyerId(20L)
                .status(ChatRoomStatus.ACTIVE).build();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/chat-rooms/" + chatRoomId);
        accessor.setUser(new StompPrincipal(nonParticipantId));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(chatRoomPort.findById(chatRoomId)).willReturn(Optional.of(room));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 구독 시 예외가 발생한다")
    void nonExistentRoomIsRejected() {
        Long chatRoomId = 999L;

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/chat-rooms/" + chatRoomId);
        accessor.setUser(new StompPrincipal(10L));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        given(chatRoomPort.findById(chatRoomId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("허용되지 않는 경로 구독 시 예외가 발생한다")
    void invalidDestinationIsRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/invalid-path");
        accessor.setUser(new StompPrincipal(10L));
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    @DisplayName("SUBSCRIBE가 아닌 명령은 통과한다")
    void nonSubscribeCommandPassesThrough() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/chat-rooms/1/send");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
    }
}
