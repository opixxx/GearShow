package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 3D 모델 JPA 저장소.
 */
public interface Showcase3dModelJpaRepository extends JpaRepository<Showcase3dModelJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 3D 모델을 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 3D 모델 JPA 엔티티 Optional
     */
    Optional<Showcase3dModelJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스 중 3D 모델이 존재하는 쇼케이스 ID를 조회한다.
     */
    @Query("SELECT m.showcaseId FROM Showcase3dModelJpaEntity m" +
            " WHERE m.showcaseId IN :showcaseIds")
    List<Long> findShowcaseIdsByShowcaseIds(@Param("showcaseIds") List<Long> showcaseIds);
}
