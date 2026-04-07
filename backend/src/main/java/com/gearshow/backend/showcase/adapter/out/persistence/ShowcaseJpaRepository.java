package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 쇼케이스 JPA 저장소.
 */
public interface ShowcaseJpaRepository extends JpaRepository<ShowcaseJpaEntity, Long> {

    /**
     * 소유자 ID로 쇼케이스 목록을 조회한다.
     */
    List<ShowcaseJpaEntity> findByOwnerId(Long ownerId);

    // ── 공개 목록 조회 (ACTIVE, 최신순) ──

    /**
     * 첫 페이지 쇼케이스 목록 조회 (커서 없음).
     */
    @Query("SELECT s FROM ShowcaseJpaEntity s WHERE s.status = 'ACTIVE'" +
            " ORDER BY s.createdAt DESC, s.id DESC")
    List<ShowcaseJpaEntity> findAllFirstPage(Pageable pageable);

    /**
     * 커서 기반 쇼케이스 목록 조회 (Keyset Pagination).
     * 정렬: createdAt DESC, id DESC.
     */
    @Query("SELECT s FROM ShowcaseJpaEntity s WHERE s.status = 'ACTIVE'" +
            " AND (s.createdAt < :cursorCreatedAt OR" +
            "   (s.createdAt = :cursorCreatedAt AND s.id < :cursorId))" +
            " ORDER BY s.createdAt DESC, s.id DESC")
    List<ShowcaseJpaEntity> findAllWithCursor(
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // ── 내 쇼케이스 목록 조회 ──

    /**
     * 소유자 기준 첫 페이지 조회 (커서 없음).
     */
    @Query("SELECT s FROM ShowcaseJpaEntity s WHERE s.ownerId = :ownerId" +
            " AND s.status <> 'DELETED'" +
            " AND (:showcaseStatus IS NULL OR s.status = :showcaseStatus)" +
            " ORDER BY s.createdAt DESC, s.id DESC")
    List<ShowcaseJpaEntity> findByOwnerIdFirstPage(
            @Param("ownerId") Long ownerId,
            @Param("showcaseStatus") ShowcaseStatus showcaseStatus,
            Pageable pageable);

    /**
     * 소유자 기준 커서 기반 조회 (Keyset Pagination).
     */
    @Query("SELECT s FROM ShowcaseJpaEntity s WHERE s.ownerId = :ownerId" +
            " AND s.status <> 'DELETED'" +
            " AND (s.createdAt < :cursorCreatedAt OR" +
            "   (s.createdAt = :cursorCreatedAt AND s.id < :cursorId))" +
            " AND (:showcaseStatus IS NULL OR s.status = :showcaseStatus)" +
            " ORDER BY s.createdAt DESC, s.id DESC")
    List<ShowcaseJpaEntity> findByOwnerIdWithCursor(
            @Param("ownerId") Long ownerId,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("showcaseStatus") ShowcaseStatus showcaseStatus,
            Pageable pageable);
}
