package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageRequest;
import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageResponse;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @InjectMocks
    private ChatWebSocketController controller;

    @Mock
    private SendChatMessageUseCase sendChatMessageUseCase;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("메시지 발신 시 UseCase 호출 후 /topic으로 브로드캐스트한다")
    void sendMessageBroadcastsToTopic() {
        Long chatRoomId = 1L;
        Long userId = 10L;
        Instant sentAt = Instant.now();
        StompChatMessageRequest request = new StompChatMessageRequest(
                ChatMessageType.TEXT, "안녕하세요", "client-id-1");

        given(sendChatMessageUseCase.send(any(SendChatMessageCommand.class)))
                .willReturn(new SendChatMessageResult(100L, 5L, sentAt));

        controller.sendMessage(chatRoomId, request, new StompPrincipal(userId));

        ArgumentCaptor<SendChatMessageCommand> cmdCaptor = ArgumentCaptor.forClass(SendChatMessageCommand.class);
        verify(sendChatMessageUseCase).send(cmdCaptor.capture());
        SendChatMessageCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.chatRoomId()).isEqualTo(chatRoomId);
        assertThat(cmd.senderId()).isEqualTo(userId);
        assertThat(cmd.content()).isEqualTo("안녕하세요");
        assertThat(cmd.clientMessageId()).isEqualTo("client-id-1");

        ArgumentCaptor<StompChatMessageResponse> responseCaptor =
                ArgumentCaptor.forClass(StompChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/chat-rooms/1"),
                responseCaptor.capture());
        StompChatMessageResponse response = responseCaptor.getValue();
        assertThat(response.type()).isEqualTo("MESSAGE");
        assertThat(response.payload().chatMessageId()).isEqualTo(100L);
        assertThat(response.payload().seq()).isEqualTo(5L);
        assertThat(response.payload().senderId()).isEqualTo(userId);
    }
}
