package com.gearshow.backend.chat.application.service;

import com.gearshow.backend.chat.application.dto.ChatMessageResult;
import com.gearshow.backend.chat.application.port.in.ListChatMessagesUseCase;
import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
// DELETED_PLACEHOLDER는 ChatMessage에서 단일 정의
import com.gearshow.backend.common.dto.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 채팅방 메시지 히스토리 조회 유스케이스 구현체 (api-spec §8-4).
 *
 * <p>DESC로 가져온 뒤 오래된 순으로 응답한다. soft delete 메시지는 본문을
 * 플레이스홀더 문구로 치환해 내보낸다.</p>
 */
@Service
@RequiredArgsConstructor
public class ListChatMessagesService implements ListChatMessagesUseCase {

    private final ChatRoomPort chatRoomPort;
    private final ChatMessagePort chatMessagePort;

    @Override
    @Transactional(readOnly = true)
    public PageInfo<ChatMessageResult> list(Long chatRoomId, Long requesterId,
                                            Long before, int size) {
        ChatRoom room = chatRoomPort.findById(chatRoomId)
                .orElseThrow(NotFoundChatRoomException::new);
        room.validateParticipant(requesterId);

        List<ChatMessage> desc = before == null
                ? chatMessagePort.findByChatRoomIdFirstPage(chatRoomId, size)
                : chatMessagePort.findByChatRoomIdBefore(chatRoomId, before, size);

        List<ChatMessageResult> results = desc.stream()
                .map(ListChatMessagesService::toResult)
                .sorted(Comparator.comparingLong(ChatMessageResult::seq))
                .toList();

        // DESC 기준으로 size + 1 조회되었으므로 hasNext 판정은 seq 최소값 기준
        return PageInfo.of(results, size,
                ChatMessageResult::sentAt,
                ChatMessageResult::chatMessageId);
    }

    private static ChatMessageResult toResult(ChatMessage m) {
        if (m.getStatus() == ChatMessageStatus.DELETED) {
            return new ChatMessageResult(
                    m.getId(), m.getSenderId(), m.getSeq(),
                    m.getMessageType(), ChatMessage.DELETED_PLACEHOLDER, null,
                    m.getStatus(), m.getSentAt());
        }
        return ChatMessageResult.from(m);
    }
}
