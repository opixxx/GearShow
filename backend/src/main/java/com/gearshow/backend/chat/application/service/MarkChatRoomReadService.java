package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.port.in.MarkChatRoomReadUseCase;
import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatReadMarkerPort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.NotFoundChatMessageException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽음 처리 유스케이스 구현체 (api-spec §8-7).
 *
 * <p>CLOSED 상태에서도 과거 메시지 읽음 갱신은 허용된다.</p>
 */
@Service
@RequiredArgsConstructor
public class MarkChatRoomReadService implements MarkChatRoomReadUseCase {

    private final ChatRoomPort chatRoomPort;
    private final ChatMessagePort chatMessagePort;
    private final ChatReadMarkerPort chatReadMarkerPort;

    @Override
    @Transactional
    public void mark(Long chatRoomId, Long requesterId, Long lastReadMessageId) {
        ChatRoom room = chatRoomPort.findById(chatRoomId)
                .orElseThrow(NotFoundChatRoomException::new);
        room.validateParticipant(requesterId);

        ChatMessage message = chatMessagePort.findById(lastReadMessageId)
                .orElseThrow(NotFoundChatMessageException::new);
        if (!message.getChatRoomId().equals(chatRoomId)) {
            throw new NotFoundChatMessageException();
        }

        chatReadMarkerPort.upsert(chatRoomId, requesterId, lastReadMessageId);
    }
}
