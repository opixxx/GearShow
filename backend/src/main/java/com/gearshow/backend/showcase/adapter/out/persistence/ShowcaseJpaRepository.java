package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 쇼케이스 JPA 저장소.
 */
public interface ShowcaseJpaRepository extends JpaRepository<ShowcaseJpaEntity, Long> {

    /**
     * 소유자 ID로 쇼케이스 목록을 조회한다.
     *
     * @param ownerId 소유자 ID
     * @return 쇼케이스 JPA 엔티티 목록
     */
    List<ShowcaseJpaEntity> findByOwnerId(Long ownerId);
}
