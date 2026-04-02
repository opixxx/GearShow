package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 3D 모델 소스 이미지 JPA 저장소.
 */
public interface ModelSourceImageJpaRepository extends JpaRepository<ModelSourceImageJpaEntity, Long> {

    /**
     * 3D 모델 ID로 소스 이미지 목록을 조회한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @return 소스 이미지 JPA 엔티티 목록
     */
    List<ModelSourceImageJpaEntity> findByShowcase3dModelId(Long showcase3dModelId);

    /**
     * 3D 모델 ID로 소스 이미지 개수를 조회한다.
     */
    int countByShowcase3dModelId(Long showcase3dModelId);
}
