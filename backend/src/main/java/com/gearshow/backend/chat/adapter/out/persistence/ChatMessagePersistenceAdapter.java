package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.application.port.out.ChatMessagePort;
import com.gearshow.backend.chat.domain.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 채팅 메시지 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ChatMessagePersistenceAdapter implements ChatMessagePort {

    private final ChatMessageJpaRepository chatMessageJpaRepository;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    public ChatMessage save(ChatMessage message) {
        ChatMessageJpaEntity saved = chatMessageJpaRepository.save(
                chatMessageMapper.toJpaEntity(message));
        return chatMessageMapper.toDomain(saved);
    }

    @Override
    public Optional<ChatMessage> findById(Long chatMessageId) {
        return chatMessageJpaRepository.findById(chatMessageId).map(chatMessageMapper::toDomain);
    }

    @Override
    public Optional<ChatMessage> findByClientMessageId(Long chatRoomId, Long senderId,
                                                       String clientMessageId) {
        if (clientMessageId == null) {
            return Optional.empty();
        }
        return chatMessageJpaRepository
                .findByChatRoomIdAndSenderIdAndClientMessageId(chatRoomId, senderId, clientMessageId)
                .map(chatMessageMapper::toDomain);
    }

    @Override
    public long nextSeq(Long chatRoomId) {
        return chatMessageJpaRepository.findMaxSeqForUpdate(chatRoomId) + 1L;
    }

    @Override
    public List<ChatMessage> findByChatRoomIdFirstPage(Long chatRoomId, int size) {
        return chatMessageJpaRepository
                .findByChatRoomIdFirstPage(chatRoomId, PageRequest.of(0, size + 1))
                .stream()
                .map(chatMessageMapper::toDomain)
                .toList();
    }

    @Override
    public List<ChatMessage> findByChatRoomIdBefore(Long chatRoomId, Long beforeMessageId, int size) {
        return chatMessageJpaRepository
                .findByChatRoomIdBefore(chatRoomId, beforeMessageId, PageRequest.of(0, size + 1))
                .stream()
                .map(chatMessageMapper::toDomain)
                .toList();
    }
}
