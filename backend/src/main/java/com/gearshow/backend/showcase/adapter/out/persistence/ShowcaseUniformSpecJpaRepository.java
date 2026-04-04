package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 쇼케이스 유니폼 스펙 JPA 저장소.
 */
public interface ShowcaseUniformSpecJpaRepository extends JpaRepository<ShowcaseUniformSpecJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 유니폼 스펙을 조회한다.
     */
    Optional<ShowcaseUniformSpecJpaEntity> findByShowcaseId(Long showcaseId);
}
