package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 3D 모델(완성품) JPA 저장소 (ADR-010).
 */
public interface Showcase3dModelJpaRepository extends JpaRepository<Showcase3dModelJpaEntity, Long> {

    Optional<Showcase3dModelJpaEntity> findByShowcaseId(Long showcaseId);

    boolean existsByShowcaseId(Long showcaseId);

    @Query("SELECT m.showcaseId FROM Showcase3dModelJpaEntity m" +
            " WHERE m.showcaseId IN :showcaseIds")
    List<Long> findShowcaseIdsByShowcaseIds(@Param("showcaseIds") List<Long> showcaseIds);
}
