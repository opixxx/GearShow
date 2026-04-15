package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.port.in.DeleteChatMessageUseCase;
import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.NotFoundChatMessageException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 soft delete 유스케이스 구현체 (api-spec §8-8).
 */
@Service
@RequiredArgsConstructor
public class DeleteChatMessageService implements DeleteChatMessageUseCase {

    private final ChatRoomPort chatRoomPort;
    private final ChatMessagePort chatMessagePort;

    @Override
    @Transactional
    public void delete(Long chatRoomId, Long chatMessageId, Long requesterId) {
        ChatRoom room = chatRoomPort.findById(chatRoomId)
                .orElseThrow(NotFoundChatRoomException::new);
        room.validateParticipant(requesterId);

        ChatMessage message = chatMessagePort.findById(chatMessageId)
                .orElseThrow(NotFoundChatMessageException::new);
        if (!message.getChatRoomId().equals(chatRoomId)) {
            throw new NotFoundChatMessageException();
        }

        ChatMessage deleted = message.softDelete(requesterId);
        chatMessagePort.save(deleted);
    }
}
