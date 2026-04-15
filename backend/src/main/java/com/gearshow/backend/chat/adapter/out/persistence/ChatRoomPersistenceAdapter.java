package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.application.dto.ChatRoomListProjection;
import com.gearshow.backend.chat.application.port.out.ChatRoomPort;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import com.gearshow.backend.chat.domain.model.ChatRoom;
import com.gearshow.backend.chat.domain.vo.ChatMessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 채팅방 Persistence Adapter.
 *
 * <p>목록 프로젝션은 (1) 채팅방 본체 (2) room별 last message id (3) last message 본문 (4) unread count
 * 를 각각 배치 조회 후 조립한다. 개별 N+1을 회피하면서 JPQL 표준 내에 머무른다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ChatRoomPersistenceAdapter implements ChatRoomPort {

    private final ChatRoomJpaRepository chatRoomJpaRepository;
    private final ChatMessageJpaRepository chatMessageJpaRepository;
    private final ChatRoomMapper chatRoomMapper;

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        ChatRoomJpaEntity saved = chatRoomJpaRepository.save(chatRoomMapper.toJpaEntity(chatRoom));
        return chatRoomMapper.toDomain(saved);
    }

    @Override
    public Optional<ChatRoom> findById(Long chatRoomId) {
        return chatRoomJpaRepository.findById(chatRoomId).map(chatRoomMapper::toDomain);
    }

    @Override
    public Optional<ChatRoom> findByShowcaseIdAndBuyerId(Long showcaseId, Long buyerId) {
        return chatRoomJpaRepository.findByShowcaseIdAndBuyerId(showcaseId, buyerId)
                .map(chatRoomMapper::toDomain);
    }

    @Override
    public List<ChatRoomListProjection> findByParticipantFirstPage(Long userId, int size) {
        List<ChatRoomJpaEntity> rooms = chatRoomJpaRepository
                .findByParticipantFirstPage(userId, PageRequest.of(0, size + 1));
        return assemble(rooms, userId);
    }

    @Override
    public List<ChatRoomListProjection> findByParticipantWithCursor(Long userId,
                                                                    Instant cursorLastActivityAt,
                                                                    Long cursorChatRoomId,
                                                                    int size) {
        List<ChatRoomJpaEntity> rooms = chatRoomJpaRepository.findByParticipantWithCursor(
                userId, cursorLastActivityAt, cursorChatRoomId,
                PageRequest.of(0, size + 1));
        return assemble(rooms, userId);
    }

    private List<ChatRoomListProjection> assemble(List<ChatRoomJpaEntity> rooms, Long userId) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = rooms.stream().map(ChatRoomJpaEntity::getId).toList();

        Map<Long, Long> lastIdByRoom = new HashMap<>();
        for (Object[] row : chatMessageJpaRepository.findLastMessageIdsByChatRoomIds(roomIds)) {
            lastIdByRoom.put((Long) row[0], (Long) row[1]);
        }

        Set<Long> lastMessageIds = lastIdByRoom.values().stream().collect(Collectors.toSet());
        Map<Long, ChatMessageJpaEntity> lastMessageById = lastMessageIds.isEmpty()
                ? Map.of()
                : chatMessageJpaRepository.findAllByIdIn(List.copyOf(lastMessageIds)).stream()
                        .collect(Collectors.toMap(ChatMessageJpaEntity::getId, m -> m));

        Map<Long, Long> unreadByRoom = new HashMap<>();
        for (Object[] row : chatMessageJpaRepository.countUnreadByChatRoomIds(roomIds, userId)) {
            unreadByRoom.put((Long) row[0], ((Number) row[1]).longValue());
        }

        List<ChatRoomListProjection> result = new ArrayList<>(rooms.size());
        for (ChatRoomJpaEntity room : rooms) {
            Long lastId = lastIdByRoom.get(room.getId());
            ChatMessageJpaEntity last = lastId != null ? lastMessageById.get(lastId) : null;
            long unread = unreadByRoom.getOrDefault(room.getId(), 0L);

            String content = last == null
                    ? null
                    : (last.getStatus() == ChatMessageStatus.DELETED
                            ? ChatMessage.DELETED_PLACEHOLDER
                            : last.getContent());

            result.add(new ChatRoomListProjection(
                    room.getId(),
                    room.getShowcaseId(),
                    room.getSellerId(),
                    room.getBuyerId(),
                    room.getStatus(),
                    room.getCreatedAt(),
                    room.getLastMessageAt(),
                    last != null ? last.getId() : null,
                    last != null ? last.getMessageType() : null,
                    content,
                    last != null ? last.getSentAt() : null,
                    unread));
        }
        return result;
    }
}
