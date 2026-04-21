package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageRequest;
import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageResponse;
import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.dto.SendChatMessageResult;
import com.gearshow.backend.chat.application.port.in.SendChatMessageUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 메시지 수신 컨트롤러 (api-spec §8-6).
 *
 * <p>클라이언트가 {@code /app/chat-rooms/{chatRoomId}/send}로 메시지를 보내면
 * UseCase를 호출하여 저장 후 {@code /topic/chat-rooms/{chatRoomId}}로 브로드캐스트한다.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SendChatMessageUseCase sendChatMessageUseCase;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat-rooms/{chatRoomId}/send")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            StompChatMessageRequest request,
            Principal principal) {

        Long userId = ((StompPrincipal) principal).userId();

        SendChatMessageResult result = sendChatMessageUseCase.send(new SendChatMessageCommand(
                chatRoomId,
                userId,
                request.messageType(),
                request.content(),
                request.clientMessageId()));

        StompChatMessageResponse response = StompChatMessageResponse.of(
                result.chatMessageId(),
                chatRoomId,
                userId,
                result.seq(),
                request.messageType(),
                request.content(),
                null,
                result.sentAt());

        messagingTemplate.convertAndSend("/topic/chat-rooms/" + chatRoomId, response);
    }
}
