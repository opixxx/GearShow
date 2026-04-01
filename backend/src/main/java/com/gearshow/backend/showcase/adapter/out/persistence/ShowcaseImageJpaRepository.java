package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쇼케이스 이미지 JPA 저장소.
 */
public interface ShowcaseImageJpaRepository extends JpaRepository<ShowcaseImageJpaEntity, Long> {

    /**
     * 쇼케이스 ID로 이미지 목록을 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 쇼케이스 이미지 JPA 엔티티 목록
     */
    List<ShowcaseImageJpaEntity> findByShowcaseId(Long showcaseId);
}
