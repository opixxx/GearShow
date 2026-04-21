package com.gearshow.backend.chat.adapter.in.websocket;

import com.gearshow.backend.chat.adapter.in.websocket.dto.StompChatMessageRequest;
import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.port.in.SendChatMessageUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 메시지 수신 컨트롤러 (api-spec §8-6).
 *
 * <p>클라이언트가 {@code /app/chat-rooms/{chatRoomId}/send} 로 메시지를 보내면 UseCase 를
 * 호출해 저장한다. 브로드캐스트는 {@code SendChatMessageService} 가 저장 트랜잭션 커밋 후
 * 발행하는 이벤트({@code ChatMessageCreatedEvent}) 를 리스너가 수신해 수행하므로 여기서는
 * 직접 수행하지 않는다 (ADR-009).</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SendChatMessageUseCase sendChatMessageUseCase;

    @MessageMapping("/chat-rooms/{chatRoomId}/send")
    public void sendMessage(
            @DestinationVariable Long chatRoomId,
            StompChatMessageRequest request,
            Principal principal) {

        if (!(principal instanceof StompPrincipal stompPrincipal)) {
            throw new MessageDeliveryException("인증되지 않은 WebSocket 세션입니다.");
        }
        Long userId = stompPrincipal.userId();

        sendChatMessageUseCase.send(new SendChatMessageCommand(
                chatRoomId,
                userId,
                request.messageType(),
                request.content(),
                request.clientMessageId()));
    }
}
