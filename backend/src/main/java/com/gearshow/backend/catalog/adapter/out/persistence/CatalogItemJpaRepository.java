package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * 카탈로그 아이템 JPA 저장소.
 */
public interface CatalogItemJpaRepository extends JpaRepository<CatalogItemJpaEntity, Long> {

    /**
     * 동일 카테고리 내 모델 코드 중복 여부를 확인한다.
     */
    boolean existsByCategoryAndModelCode(Category category, String modelCode);

    /**
     * 카테고리와 브랜드 조건에 해당하는 카탈로그 아이템 ID 목록을 조회한다.
     */
    @Query("SELECT c.id FROM CatalogItemJpaEntity c" +
            " WHERE (:category IS NULL OR c.category = :category)" +
            " AND (:brand IS NULL OR c.brand = :brand)")
    List<Long> findIdsByCategoryAndBrand(
            @Param("category") Category category,
            @Param("brand") String brand);

    /**
     * 첫 페이지 카탈로그 아이템 목록 조회 (커서 없음).
     * 카테고리·키워드 조건이 null이면 해당 조건은 생략된다.
     * 키워드는 어댑터가 escape + {@code %keyword%} 형태로 와일드카드를 부여하여 전달하며,
     * LIKE는 {@code ESCAPE '\\'}와 짝을 이뤄 메타문자(% / _ / \\)를 리터럴로 매칭한다.
     */
    @Query("""
            SELECT c FROM CatalogItemJpaEntity c
            WHERE c.status = :status
              AND (:category IS NULL OR c.category = :category)
              AND (:keyword IS NULL
                   OR c.brand LIKE :keyword ESCAPE '\\'
                   OR c.modelCode LIKE :keyword ESCAPE '\\'
                   OR c.fullNameKo LIKE :keyword ESCAPE '\\'
                   OR c.fullNameEn LIKE :keyword ESCAPE '\\')
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<CatalogItemJpaEntity> findAllFirstPage(
            @Param("status") CatalogStatus status,
            @Param("category") Category category,
            @Param("keyword") String keyword,
            Pageable pageable);

    /**
     * 커서 기반 카탈로그 아이템 목록 조회 (Keyset Pagination).
     * 정렬: createdAt DESC, id DESC.
     * 카테고리·키워드 조건이 null이면 해당 조건은 생략된다.
     */
    @Query("""
            SELECT c FROM CatalogItemJpaEntity c
            WHERE c.status = :status
              AND (:category IS NULL OR c.category = :category)
              AND (:keyword IS NULL
                   OR c.brand LIKE :keyword ESCAPE '\\'
                   OR c.modelCode LIKE :keyword ESCAPE '\\'
                   OR c.fullNameKo LIKE :keyword ESCAPE '\\'
                   OR c.fullNameEn LIKE :keyword ESCAPE '\\')
              AND (c.createdAt < :cursorCreatedAt OR
                  (c.createdAt = :cursorCreatedAt AND c.id < :cursorId))
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<CatalogItemJpaEntity> findAllWithCursor(
            @Param("status") CatalogStatus status,
            @Param("category") Category category,
            @Param("keyword") String keyword,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
