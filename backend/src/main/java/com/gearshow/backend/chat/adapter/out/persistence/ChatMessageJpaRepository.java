package com.gearshow.backend.chat.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 채팅 메시지 JPA 저장소.
 */
public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageJpaEntity, Long> {

    Optional<ChatMessageJpaEntity> findByChatRoomIdAndSenderIdAndClientMessageId(
            Long chatRoomId, Long senderId, String clientMessageId);

    /**
     * 채팅방 내 현재 최대 seq 값 (FOR UPDATE lock 포함).
     * 없으면 0 반환.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM ChatMessageJpaEntity m"
            + " WHERE m.chatRoomId = :chatRoomId")
    long findMaxSeqForUpdate(@Param("chatRoomId") Long chatRoomId);

    /**
     * 채팅방의 최신 메시지부터 역순으로 size+1 조회.
     */
    @Query("SELECT m FROM ChatMessageJpaEntity m"
            + " WHERE m.chatRoomId = :chatRoomId"
            + " ORDER BY m.id DESC")
    List<ChatMessageJpaEntity> findByChatRoomIdFirstPage(@Param("chatRoomId") Long chatRoomId,
                                                         Pageable pageable);

    /**
     * {@code chatMessageId < before} 조건 역순 size+1 조회.
     */
    @Query("SELECT m FROM ChatMessageJpaEntity m"
            + " WHERE m.chatRoomId = :chatRoomId AND m.id < :before"
            + " ORDER BY m.id DESC")
    List<ChatMessageJpaEntity> findByChatRoomIdBefore(@Param("chatRoomId") Long chatRoomId,
                                                      @Param("before") Long before,
                                                      Pageable pageable);

    /**
     * 채팅방 목록용 마지막 메시지 배치 조회.
     * room별 MAX(id)를 구한 뒤 그 ID 목록을 다시 조회하는 2단계.
     */
    @Query("SELECT m FROM ChatMessageJpaEntity m WHERE m.id IN :ids")
    List<ChatMessageJpaEntity> findAllByIdIn(@Param("ids") List<Long> ids);

    /**
     * 채팅방별 마지막 메시지 ID (MAX).
     */
    @Query("SELECT m.chatRoomId, MAX(m.id) FROM ChatMessageJpaEntity m"
            + " WHERE m.chatRoomId IN :chatRoomIds"
            + " GROUP BY m.chatRoomId")
    List<Object[]> findLastMessageIdsByChatRoomIds(@Param("chatRoomIds") List<Long> chatRoomIds);

    /**
     * 참여자의 unread count 배치 조회.
     * 상대방 발신 + ACTIVE 상태 + (marker null 이거나 id &gt; lastReadMessageId) 메시지 count.
     *
     * <p>read marker 서브쿼리를 {@code COALESCE}로 포함해 채팅방별 개별 마커와 일치시킨다.
     * 채팅방 당 자신의 read marker는 0 또는 1개이므로 스칼라 서브쿼리로 안전하다.</p>
     */
    @Query("SELECT m.chatRoomId, COUNT(m) FROM ChatMessageJpaEntity m"
            + " WHERE m.chatRoomId IN :chatRoomIds"
            + "   AND m.status = com.gearshow.backend.chat.domain.vo.ChatMessageStatus.ACTIVE"
            + "   AND (m.senderId IS NULL OR m.senderId <> :userId)"
            + "   AND m.id > COALESCE("
            + "     (SELECT rm.lastReadMessageId FROM ChatReadMarkerJpaEntity rm"
            + "      WHERE rm.chatRoomId = m.chatRoomId AND rm.userId = :userId), 0)"
            + " GROUP BY m.chatRoomId")
    List<Object[]> countUnreadByChatRoomIds(@Param("chatRoomIds") List<Long> chatRoomIds,
                                            @Param("userId") Long userId);
}
