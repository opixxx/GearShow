package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageRequest;
import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.dto.SendChatMessageResult;
import com.gearshow.backend.chat.application.port.in.SendChatMessageUseCase;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @InjectMocks
    private ChatWebSocketController controller;

    @Mock
    private SendChatMessageUseCase sendChatMessageUseCase;

    @Test
    @DisplayName("메시지 발신 시 UseCase 에 커맨드가 전달된다 (브로드캐스트는 AFTER_COMMIT 리스너가 담당)")
    void sendMessage_delegatesToUseCase() {
        // given
        Long chatRoomId = 1L;
        Long userId = 10L;
        Instant sentAt = Instant.parse("2026-04-22T00:00:00Z");
        StompChatMessageRequest request = new StompChatMessageRequest(
                ChatMessageType.TEXT, "안녕하세요", "client-id-1");
        given(sendChatMessageUseCase.send(any(SendChatMessageCommand.class)))
                .willReturn(new SendChatMessageResult(100L, 5L, sentAt));

        // when
        controller.sendMessage(chatRoomId, request, new StompPrincipal(userId));

        // then — 컨트롤러는 커맨드 전달만 책임지고, 브로드캐스트는 이벤트 리스너가 처리
        ArgumentCaptor<SendChatMessageCommand> cmdCaptor =
                ArgumentCaptor.forClass(SendChatMessageCommand.class);
        verify(sendChatMessageUseCase).send(cmdCaptor.capture());
        SendChatMessageCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.chatRoomId()).isEqualTo(chatRoomId);
        assertThat(cmd.senderId()).isEqualTo(userId);
        assertThat(cmd.content()).isEqualTo("안녕하세요");
        assertThat(cmd.clientMessageId()).isEqualTo("client-id-1");
    }

    @Test
    @DisplayName("principal 이 StompPrincipal 이 아니면 MessageDeliveryException 을 던지고 UseCase 호출하지 않는다")
    void sendMessage_invalidPrincipal_rejects() {
        // given
        StompChatMessageRequest request = new StompChatMessageRequest(
                ChatMessageType.TEXT, "hi", null);

        // when & then
        assertThatThrownBy(() -> controller.sendMessage(1L, request, () -> "anon"))
                .isInstanceOf(MessageDeliveryException.class);
        verify(sendChatMessageUseCase, never()).send(any(SendChatMessageCommand.class));
    }
}
