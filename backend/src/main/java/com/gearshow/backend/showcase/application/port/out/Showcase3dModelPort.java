package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.Showcase3dModel;

import java.util.Optional;

/**
 * 쇼케이스 3D 모델 Outbound Port.
 */
public interface Showcase3dModelPort {

    /**
     * 3D 모델을 저장한다.
     */
    Showcase3dModel save(Showcase3dModel model);

    /**
     * ID로 3D 모델을 조회한다.
     */
    Optional<Showcase3dModel> findById(Long id);

    /**
     * 쇼케이스 ID로 3D 모델을 조회한다.
     */
    Optional<Showcase3dModel> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID에 해당하는 3D 모델 존재 여부를 확인한다.
     */
    boolean existsByShowcaseId(Long showcaseId);

    /**
     * 여러 쇼케이스에 대해 3D 모델이 존재하는 쇼케이스 ID 목록을 반환한다.
     *
     * @param showcaseIds 쇼케이스 ID 목록
     * @return 3D 모델이 존재하는 쇼케이스 ID Set
     */
    java.util.Set<Long> findShowcaseIdsWithModel(java.util.List<Long> showcaseIds);
}
