package com.gearshow.backend.chat.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 채팅방 읽음 마커 JPA 저장소.
 */
public interface ChatReadMarkerJpaRepository extends JpaRepository<ChatReadMarkerJpaEntity, Long> {

    Optional<ChatReadMarkerJpaEntity> findByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    /**
     * {@code ON DUPLICATE KEY UPDATE} 기반 atomic upsert.
     *
     * <p>
     *   도메인 불변식 "lastReadMessageId 역진 금지"를 {@code GREATEST}로 DB 레벨에서 보장한다.
     *   PESSIMISTIC_WRITE + SELECT/INSERT 분리 방식 대비 (1) 경합 시 UNIQUE 충돌 가능성 제거,
     *   (2) 단일 왕복으로 완료, (3) 갭락 의존 회피.
     * </p>
     *
     * <p>MySQL 전용 구문이라 native query로 선언한다.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO chat_read_marker"
            + " (chat_room_id, user_id, last_read_message_id, updated_at)"
            + " VALUES (:chatRoomId, :userId, :lastReadMessageId, :now)"
            + " ON DUPLICATE KEY UPDATE"
            + "   last_read_message_id = GREATEST("
            + "     COALESCE(last_read_message_id, 0), VALUES(last_read_message_id)),"
            + "   updated_at = VALUES(updated_at)",
            nativeQuery = true)
    void upsert(@Param("chatRoomId") Long chatRoomId,
                @Param("userId") Long userId,
                @Param("lastReadMessageId") Long lastReadMessageId,
                @Param("now") Instant now);
}
