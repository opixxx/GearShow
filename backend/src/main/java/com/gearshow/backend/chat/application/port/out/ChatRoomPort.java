package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.application.dto.ChatRoomListProjection;
import com.gearshow.backend.chat.domain.model.ChatRoom;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 채팅방 Outbound Port.
 */
public interface ChatRoomPort {

    ChatRoom save(ChatRoom chatRoom);

    Optional<ChatRoom> findById(Long chatRoomId);

    /**
     * {@code (showcaseId, buyerId)} 유니크 키로 기존 채팅방을 조회한다.
     */
    Optional<ChatRoom> findByShowcaseIdAndBuyerId(Long showcaseId, Long buyerId);

    /**
     * 참여자 기준 채팅방 목록 첫 페이지.
     * {@code size + 1} 만큼 조회해 hasNext 판정에 사용한다.
     *
     * @param userId 참여자(판매자 또는 구매자) ID
     * @param size   페이지 크기
     */
    List<ChatRoomListProjection> findByParticipantFirstPage(Long userId, int size);

    /**
     * 참여자 기준 채팅방 목록 커서 페이지.
     * 정렬 기준: {@code (lastActivityAt DESC, chatRoomId DESC)}. {@code lastActivityAt}은
     * {@code COALESCE(lastMessageAt, createdAt)}로 계산한다.
     */
    List<ChatRoomListProjection> findByParticipantWithCursor(Long userId,
                                                             Instant cursorLastActivityAt,
                                                             Long cursorChatRoomId,
                                                             int size);
}
