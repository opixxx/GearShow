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

import java.util.ArrayList;
import java.util.Collections;
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

        // 히스토리는 항상 DESC(최신→과거)로 size+1 조회한다.
        // PageInfo.of 가 size+1 의 마지막 원소(=가장 과거)로 nextCursor 를 만들어야
        // "다음 페이지 = 더 과거" 의미가 보장된다. 따라서 정렬을 뒤집는 작업은 응답 직전에만 수행.
        List<ChatMessage> desc = before == null
                ? chatMessagePort.findByChatRoomIdFirstPage(chatRoomId, size)
                : chatMessagePort.findByChatRoomIdBefore(chatRoomId, before, size);

        List<ChatMessageResult> descResults = desc.stream()
                .map(ListChatMessagesService::toResult)
                .toList();

        PageInfo<ChatMessageResult> pageDesc = PageInfo.of(descResults, size,
                ChatMessageResult::sentAt,
                ChatMessageResult::chatMessageId);

        // 응답은 ASC(과거→최신) 로 노출. 커서/hasNext 는 위에서 계산된 값 그대로 유지.
        List<ChatMessageResult> ascData = new ArrayList<>(pageDesc.data());
        Collections.reverse(ascData);
        return new PageInfo<>(pageDesc.pageToken(), ascData, pageDesc.size(), pageDesc.hasNext());
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
