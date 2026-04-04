package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 쇼케이스 축구화 스펙 JPA 저장소.
 */
public interface ShowcaseBootsSpecJpaRepository extends JpaRepository<ShowcaseBootsSpecJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 축구화 스펙을 조회한다.
     */
    Optional<ShowcaseBootsSpecJpaEntity> findByShowcaseId(Long showcaseId);
}
