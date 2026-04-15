package com.gearshow.backend.chat.adapter.out.persistence;

import com.gearshow.backend.chat.application.port.out.ChatReadMarkerPort;
import com.gearshow.backend.chat.domain.exception.NotFoundChatRoomException;
import com.gearshow.backend.chat.domain.model.ChatReadMarker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 채팅방 읽음 마커 Persistence Adapter.
 *
 * <p>{@link #upsert}는 MySQL {@code INSERT ... ON DUPLICATE KEY UPDATE} 네이티브 쿼리로
 * 단일 왕복 · 경합 시에도 안전하게 수행된다. 역진 방지는 SQL {@code GREATEST}로 보장한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ChatReadMarkerPersistenceAdapter implements ChatReadMarkerPort {

    private final ChatReadMarkerJpaRepository chatReadMarkerJpaRepository;
    private final ChatReadMarkerMapper chatReadMarkerMapper;

    @Override
    public Optional<ChatReadMarker> findByChatRoomIdAndUserId(Long chatRoomId, Long userId) {
        return chatReadMarkerJpaRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .map(chatReadMarkerMapper::toDomain);
    }

    @Override
    public ChatReadMarker upsert(Long chatRoomId, Long userId, Long lastReadMessageId) {
        chatReadMarkerJpaRepository.upsert(chatRoomId, userId, lastReadMessageId, Instant.now());
        // upsert 이후 최신 상태를 도메인으로 반환
        return chatReadMarkerJpaRepository.findByChatRoomIdAndUserId(chatRoomId, userId)
                .map(chatReadMarkerMapper::toDomain)
                .orElseThrow(NotFoundChatRoomException::new);
    }
}
