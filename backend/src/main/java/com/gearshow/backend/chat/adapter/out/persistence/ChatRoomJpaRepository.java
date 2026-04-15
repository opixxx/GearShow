package com.gearshow.backend.chat.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 채팅방 JPA 저장소.
 */
public interface ChatRoomJpaRepository extends JpaRepository<ChatRoomJpaEntity, Long> {

    /**
     * {@code (showcaseId, buyerId)} 유니크 키 조회.
     */
    Optional<ChatRoomJpaEntity> findByShowcaseIdAndBuyerId(Long showcaseId, Long buyerId);

    /**
     * 참여자 기준 목록 첫 페이지 ({@code lastMessageAt DESC, id DESC}).
     * {@code lastMessageAt}은 NOT NULL 이므로 복합 인덱스가 그대로 활용된다.
     */
    @Query("SELECT cr FROM ChatRoomJpaEntity cr"
            + " WHERE cr.sellerId = :userId OR cr.buyerId = :userId"
            + " ORDER BY cr.lastMessageAt DESC, cr.id DESC")
    List<ChatRoomJpaEntity> findByParticipantFirstPage(@Param("userId") Long userId,
                                                       Pageable pageable);

    /**
     * 참여자 기준 커서 페이지.
     */
    @Query("SELECT cr FROM ChatRoomJpaEntity cr"
            + " WHERE (cr.sellerId = :userId OR cr.buyerId = :userId)"
            + " AND ("
            + "   cr.lastMessageAt < :cursorActivityAt"
            + "   OR (cr.lastMessageAt = :cursorActivityAt AND cr.id < :cursorId)"
            + " )"
            + " ORDER BY cr.lastMessageAt DESC, cr.id DESC")
    List<ChatRoomJpaEntity> findByParticipantWithCursor(@Param("userId") Long userId,
                                                        @Param("cursorActivityAt") Instant cursorActivityAt,
                                                        @Param("cursorId") Long cursorId,
                                                        Pageable pageable);
}
