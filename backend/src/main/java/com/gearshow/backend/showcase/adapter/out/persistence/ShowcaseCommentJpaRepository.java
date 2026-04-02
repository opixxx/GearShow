package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 쇼케이스 댓글 JPA 저장소.
 */
public interface ShowcaseCommentJpaRepository extends JpaRepository<ShowcaseCommentJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 댓글 목록을 조회한다.
     */
    List<ShowcaseCommentJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 첫 페이지 ACTIVE 댓글 조회 (커서 없음).
     */
    @Query("SELECT c FROM ShowcaseCommentJpaEntity c" +
            " WHERE c.showcaseId = :showcaseId AND c.status = 'ACTIVE'" +
            " ORDER BY c.createdAt DESC, c.id DESC")
    List<ShowcaseCommentJpaEntity> findByShowcaseIdFirstPage(
            @Param("showcaseId") Long showcaseId,
            Pageable pageable);

    /**
     * 커서 기반 ACTIVE 댓글 조회 (Keyset Pagination).
     */
    @Query("SELECT c FROM ShowcaseCommentJpaEntity c" +
            " WHERE c.showcaseId = :showcaseId AND c.status = 'ACTIVE'" +
            " AND (c.createdAt < :cursorCreatedAt OR" +
            "   (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))" +
            " ORDER BY c.createdAt DESC, c.id DESC")
    List<ShowcaseCommentJpaEntity> findByShowcaseIdWithCursor(
            @Param("showcaseId") Long showcaseId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /**
     * 쇼케이스 ID로 ACTIVE 댓글 개수를 조회한다.
     */
    @Query("SELECT COUNT(c) FROM ShowcaseCommentJpaEntity c" +
            " WHERE c.showcaseId = :showcaseId AND c.status = 'ACTIVE'")
    int countActiveByShowcaseId(@Param("showcaseId") Long showcaseId);

    /**
     * 여러 쇼케이스의 ACTIVE 댓글 개수를 일괄 조회한다.
     * 결과: [showcaseId, count] 배열 목록
     */
    @Query("SELECT c.showcaseId, COUNT(c) FROM ShowcaseCommentJpaEntity c" +
            " WHERE c.showcaseId IN :showcaseIds AND c.status = 'ACTIVE'" +
            " GROUP BY c.showcaseId")
    List<Object[]> countActiveByShowcaseIds(@Param("showcaseIds") List<Long> showcaseIds);

    /**
     * 쇼케이스에 속한 모든 ACTIVE 댓글을 DELETED 상태로 일괄 변경한다.
     */
    @Modifying
    @Query("UPDATE ShowcaseCommentJpaEntity c SET c.status = 'DELETED', c.updatedAt = CURRENT_TIMESTAMP" +
            " WHERE c.showcaseId = :showcaseId AND c.status = 'ACTIVE'")
    void softDeleteAllByShowcaseId(@Param("showcaseId") Long showcaseId);
}
