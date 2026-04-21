package com.gearshow.backend.chat.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     *
     * <p><b>⚠️ Filesort 위험</b>: {@code sellerId OR buyerId} 조건은 {@code (seller_id, last_message_at)},
     * {@code (buyer_id, last_message_at)} 두 인덱스를 {@code index_merge(sort_union)} 로만 활용 가능하여
     * 정렬이 merge 이후 다시 filesort 로 수행될 수 있다. 채팅방이 수백만 행을 넘어가면 체감 성능이 저하된다.
     * 근본 해결은 참여자 정규화 컬럼({@code participant_a_id, participant_b_id}) 도입이며, 데이터 마이그레이션
     * 리스크가 커 별도 작업(Phase 3) 으로 분리한다. 현재 규모에서는 주석으로만 기록.</p>
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

    /**
     * 새 메시지 도착 시 {@code lastMessageAt} 만 타겟 업데이트 한다.
     *
     * <p>{@code WHERE last_message_at IS NULL OR last_message_at < :sentAt} 조건으로 시간 역진을 DB 에서 차단한다.
     * 영향 row 0 = 시간 역진 또는 {@code chatRoomId} 부재로 no-op.</p>
     *
     * <p>{@code flushAutomatically + clearAutomatically} 는 같은 트랜잭션에서 동일 엔티티를 재조회할 때
     * 영속성 컨텍스트에 캐시된 stale 인스턴스가 반환되는 것을 막기 위함 (통합 테스트 및 향후 서비스 확장 보호).</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ChatRoomJpaEntity cr SET cr.lastMessageAt = :sentAt"
            + " WHERE cr.id = :chatRoomId"
            + " AND (cr.lastMessageAt IS NULL OR cr.lastMessageAt < :sentAt)")
    int updateLastMessageAt(@Param("chatRoomId") Long chatRoomId,
                            @Param("sentAt") Instant sentAt);
}
