package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
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

    // ── 공개 목록 조회 (ACTIVE) ──

    /**
     * 첫 페이지 쇼케이스 목록 조회 (커서 없음).
     * category, brand로 직접 필터링한다.
     */
    @Query("SELECT s FROM ShowcaseJpaEntity s WHERE s.status = 'ACTIVE'" +
            " AND (:category IS NULL OR s.category = :category)" +
            " AND (:brand IS NULL OR s.brand = :brand)" +
            " AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%'))" +
            " AND (:isForSale IS NULL OR s.forSale = :isForSale)" +
            " AND (:conditionGrade IS NULL OR s.conditionGrade = :conditionGrade)" +
            " ORDER BY s.createdAt DESC, s.id DESC")
    List<ShowcaseJpaEntity> findAllFirstPage(
            @Param("category") Category category,
            @Param("brand") String brand,
            @Param("keyword") String keyword,
            @Param("isForSale") Boolean isForSale,
            @Param("conditionGrade") ConditionGrade conditionGrade,
            Pageable pageable);

    /**
     * 커서 기반 쇼케이스 목록 조회 (Keyset Pagination).
     * 정렬: createdAt DESC, id DESC.
     */
    @Query("SELECT s FROM ShowcaseJpaEntity s WHERE s.status = 'ACTIVE'" +
            " AND (s.createdAt < :cursorCreatedAt OR" +
            "   (s.createdAt = :cursorCreatedAt AND s.id < :cursorId))" +
            " AND (:category IS NULL OR s.category = :category)" +
            " AND (:brand IS NULL OR s.brand = :brand)" +
            " AND (:keyword IS NULL OR s.title LIKE CONCAT('%', :keyword, '%'))" +
            " AND (:isForSale IS NULL OR s.forSale = :isForSale)" +
            " AND (:conditionGrade IS NULL OR s.conditionGrade = :conditionGrade)" +
            " ORDER BY s.createdAt DESC, s.id DESC")
    List<ShowcaseJpaEntity> findAllWithCursor(
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            @Param("category") Category category,
            @Param("brand") String brand,
            @Param("keyword") String keyword,
            @Param("isForSale") Boolean isForSale,
            @Param("conditionGrade") ConditionGrade conditionGrade,
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
