package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.SendChatMessageCommand;
import com.gearshow.backend.chat.application.dto.SendChatMessageResult;
import com.gearshow.backend.chat.application.port.in.SendChatMessageUseCase;
import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.DuplicateClientMessageIdException;
import com.gearshow.backend.chat.domain.exception.InvalidChatMessageException;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 송신 유스케이스 구현체 (api-spec §8-5).
 *
 * <p>Phase 1 MVP는 TEXT만 허용한다.
 * {@code clientMessageId}가 기존 메시지와 일치하면 저장 없이
 * {@link DuplicateClientMessageIdException}를 던지며 기존 식별자를 실어 보낸다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendChatMessageService implements SendChatMessageUseCase {

    /** seq UNIQUE 충돌 시 재시도 횟수. 빈 채팅방 첫 메시지 동시 race 흡수용. */
    private static final int SEQ_CONFLICT_MAX_RETRY = 3;

    private final ChatRoomPort chatRoomPort;
    private final ChatMessagePort chatMessagePort;

    @Override
    @Transactional
    public SendChatMessageResult send(SendChatMessageCommand command) {
        if (command.messageType() != ChatMessageType.TEXT) {
            throw new InvalidChatMessageException();
        }

        ChatRoom room = chatRoomPort.findById(command.chatRoomId())
                .orElseThrow(NotFoundChatRoomException::new);
        room.validateParticipant(command.senderId());
        room.validateSendable();

        if (command.clientMessageId() != null) {
            chatMessagePort.findByClientMessageId(
                            command.chatRoomId(), command.senderId(), command.clientMessageId())
                    .ifPresent(existing -> {
                        throw new DuplicateClientMessageIdException(
                                existing.getId(), existing.getSeq(), existing.getSentAt());
                    });
        }

        ChatMessage saved = saveWithSeqRetry(command);
        chatRoomPort.save(room.touch(saved.getSentAt()));

        return new SendChatMessageResult(saved.getId(), saved.getSeq(), saved.getSentAt());
    }

    /**
     * UNIQUE(chat_room_id, seq) 충돌 발생 시 (빈 채팅방에서 동시 첫 메시지 등) 다음 seq로 재시도한다.
     * 같은 트랜잭션 내 재시도라 일관성은 유지되며, 최대 시도 초과 시 예외를 그대로 전파.
     */
    private ChatMessage saveWithSeqRetry(SendChatMessageCommand command) {
        DataIntegrityViolationException last = null;
        for (int attempt = 1; attempt <= SEQ_CONFLICT_MAX_RETRY; attempt++) {
            long seq = chatMessagePort.nextSeq(command.chatRoomId());
            ChatMessage message = ChatMessage.text(
                    command.chatRoomId(), command.senderId(), seq,
                    command.content(), command.clientMessageId());
            try {
                return chatMessagePort.save(message);
            } catch (DataIntegrityViolationException e) {
                last = e;
                log.warn("seq UNIQUE 충돌, 재시도 {}/{} (chatRoomId={}, seq={})",
                        attempt, SEQ_CONFLICT_MAX_RETRY, command.chatRoomId(), seq);
            }
        }
        throw last;
    }
}
