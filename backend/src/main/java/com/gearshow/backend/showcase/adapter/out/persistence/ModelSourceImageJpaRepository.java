package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 3D 모델 소스 이미지 JPA 저장소 (ADR-010 showcase_id 직접 참조).
 */
public interface ModelSourceImageJpaRepository extends JpaRepository<ModelSourceImageJpaEntity, Long> {

    List<ModelSourceImageJpaEntity> findByShowcaseIdOrderBySortOrderAsc(Long showcaseId);

    int countByShowcaseId(Long showcaseId);

    @Query("""
            SELECT m.imageUrl
              FROM ModelSourceImageJpaEntity m
             WHERE m.showcaseId = :showcaseId
             ORDER BY m.sortOrder ASC
            """)
    List<String> findImageUrlsByShowcaseId(@Param("showcaseId") Long showcaseId);
}
