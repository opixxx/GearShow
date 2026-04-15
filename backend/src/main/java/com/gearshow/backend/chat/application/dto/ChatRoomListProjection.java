package com.gearshow.backend.chat.application.dto;

import com.gearshow.backend.chat.domain.vo.ChatMessageType;
import com.gearshow.backend.chat.domain.vo.ChatRoomStatus;

import java.time.Instant;

/**
 * 채팅방 목록 Port 프로젝션.
 *
 * <p>채팅방 기본 필드 + 마지막 메시지 스냅샷 + 미읽음 카운트를 쿼리 1회에 담아온다.
 * Application 서비스는 이 프로젝션에 상대방 프로필과 쇼케이스 요약을 합쳐 {@link ChatRoomListItemResult}로 변환한다.</p>
 */
public record ChatRoomListProjection(
        Long chatRoomId,
        Long showcaseId,
        Long sellerId,
        Long buyerId,
        ChatRoomStatus status,
        Instant createdAt,
        Instant lastMessageAt,
        Long lastMessageId,
        ChatMessageType lastMessageType,
        String lastMessageContent,
        Instant lastMessageSentAt,
        long unreadCount
) {
}
