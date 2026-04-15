package com.gearshow.backend.chat.application.port.out;

import com.gearshow.backend.chat.domain.model.ChatMessage;

import java.util.List;
import java.util.Optional;

/**
 * 채팅 메시지 Outbound Port.
 */
public interface ChatMessagePort {

    ChatMessage save(ChatMessage message);

    Optional<ChatMessage> findById(Long chatMessageId);

    /**
     * 동일 {@code (chatRoomId, senderId, clientMessageId)} 조합의 기존 메시지를 조회한다.
     * 멱등성 재시도 처리용이며, {@code clientMessageId}가 null이면 항상 empty.
     */
    Optional<ChatMessage> findByClientMessageId(Long chatRoomId, Long senderId, String clientMessageId);

    /**
     * 채팅방 내부에서 다음으로 사용할 {@code seq}를 반환한다.
     * 구현체는 row lock(FOR UPDATE)으로 동시성을 보장해야 한다.
     */
    long nextSeq(Long chatRoomId);

    /**
     * 채팅방 히스토리 첫 페이지 (최신부터).
     * {@code size + 1} 조회로 hasNext 판정.
     */
    List<ChatMessage> findByChatRoomIdFirstPage(Long chatRoomId, int size);

    /**
     * {@code chatMessageId < before} 조건으로 더 오래된 메시지 페이지를 조회한다.
     */
    List<ChatMessage> findByChatRoomIdBefore(Long chatRoomId, Long beforeMessageId, int size);
}
