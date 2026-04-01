package com.gearshow.backend.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 유니폼 스펙 JPA 저장소.
 */
public interface UniformSpecJpaRepository extends JpaRepository<UniformSpecJpaEntity, Long> {

    /**
     * 카탈로그 아이템 ID로 유니폼 스펙을 조회한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @return 유니폼 스펙 JPA 엔티티 Optional
     */
    Optional<UniformSpecJpaEntity> findByCatalogItemId(Long catalogItemId);
}
