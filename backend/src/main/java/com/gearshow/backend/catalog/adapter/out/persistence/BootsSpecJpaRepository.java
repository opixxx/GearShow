package com.gearshow.backend.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 축구화 스펙 JPA 저장소.
 */
public interface BootsSpecJpaRepository extends JpaRepository<BootsSpecJpaEntity, Long> {

    /**
     * 카탈로그 아이템 ID로 축구화 스펙을 조회한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @return 축구화 스펙 JPA 엔티티 Optional
     */
    Optional<BootsSpecJpaEntity> findByCatalogItemId(Long catalogItemId);
}
