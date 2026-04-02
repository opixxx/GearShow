package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ModelSourceImage;

import java.util.List;

/**
 * 3D 모델 소스 이미지 Outbound Port.
 */
public interface ModelSourceImagePort {

    /**
     * 소스 이미지를 일괄 저장한다.
     */
    List<ModelSourceImage> saveAll(List<ModelSourceImage> images);

    /**
     * 3D 모델 ID로 소스 이미지 목록을 조회한다.
     */
    List<ModelSourceImage> findByShowcase3dModelId(Long showcase3dModelId);

    /**
     * 3D 모델 ID로 소스 이미지 개수를 조회한다.
     */
    int countByShowcase3dModelId(Long showcase3dModelId);
}
