package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * 커서 기반으로 카탈로그 아이템 목록을 조회한다.
     * ACTIVE 상태만 조회하며, 필터 조건이 null이면 무시한다.
     */
    @Query("""
            SELECT c FROM CatalogItemJpaEntity c
            WHERE c.status = 'ACTIVE'
              AND (:cursorId IS NULL OR c.id < :cursorId)
              AND (:category IS NULL OR c.category = :category)
              AND (:brand IS NULL OR c.brand = :brand)
              AND (:keyword IS NULL OR c.itemName LIKE CONCAT('%', :keyword, '%')
                   OR c.modelCode LIKE CONCAT('%', :keyword, '%'))
            ORDER BY c.id DESC
            """)
    List<CatalogItemJpaEntity> findAllWithCursor(
            @Param("cursorId") Long cursorId,
            @Param("category") Category category,
            @Param("brand") String brand,
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
}
