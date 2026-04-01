package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
