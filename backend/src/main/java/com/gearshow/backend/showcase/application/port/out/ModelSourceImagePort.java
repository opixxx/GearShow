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

    /**
     * 쇼케이스 ID 를 통해 소스 이미지의 저장 URL 목록을 {@code sort_order} 순서대로 조회한다.
     *
     * <p>워크플로우 테이블은 {@code showcaseId} 만 참조하므로, Worker TX1 에서 S3 존재
     * 검증을 수행하려면 {@code showcase3dModelId} 경유 없이 직접 조회할 수 있어야 한다.
     * 반환 값은 DB 에 저장된 원본 URL (CDN 접두어 포함) — 호출자는 {@code ImageStoragePort.existsByUrl}
     * 로 존재 여부를 확인한다.</p>
     */
    List<String> findImageUrlsByShowcaseId(Long showcaseId);
}
