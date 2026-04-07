package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 쇼케이스 스펙 JPA 저장소.
 */
public interface ShowcaseSpecJpaRepository extends JpaRepository<ShowcaseSpecJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 스펙을 조회한다.
     */
    Optional<ShowcaseSpecJpaEntity> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID 목록으로 스펙을 일괄 조회한다.
     */
    List<ShowcaseSpecJpaEntity> findByShowcaseIdIn(List<Long> showcaseIds);
}
