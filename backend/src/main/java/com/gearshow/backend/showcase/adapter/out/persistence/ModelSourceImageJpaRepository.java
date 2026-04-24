package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 3D 모델 소스 이미지 JPA 저장소.
 */
public interface ModelSourceImageJpaRepository extends JpaRepository<ModelSourceImageJpaEntity, Long> {

    /**
     * 3D 모델 ID로 소스 이미지 목록을 조회한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @return 소스 이미지 JPA 엔티티 목록
     */
    List<ModelSourceImageJpaEntity> findByShowcase3dModelId(Long showcase3dModelId);

    /**
     * 3D 모델 ID로 소스 이미지 개수를 조회한다.
     */
    int countByShowcase3dModelId(Long showcase3dModelId);

    /**
     * 쇼케이스 ID 에 해당하는 소스 이미지의 URL 목록을 {@code sort_order} 오름차순으로 조회한다.
     *
     * <p>{@code Showcase3dModel} 을 경유하는 조인 쿼리 — {@code showcaseId} 는 FK + UNIQUE.
     * 옵티마이저 친화성을 위해 명시적 {@code INNER JOIN ... ON} 사용.</p>
     */
    @Query("""
            SELECT m.imageUrl
              FROM ModelSourceImageJpaEntity m
             INNER JOIN Showcase3dModelJpaEntity s
                ON s.id = m.showcase3dModelId
             WHERE s.showcaseId = :showcaseId
             ORDER BY m.sortOrder ASC
            """)
    List<String> findImageUrlsByShowcaseId(@Param("showcaseId") Long showcaseId);
}
