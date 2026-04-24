package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.domain.model.ModelSourceImage;

import java.util.List;

/**
 * 3D 모델 소스 이미지 Outbound Port.
 *
 * <p>ADR-010 프로세스 테이블 분리 이후 {@code showcase_id} 를 직접 참조한다.</p>
 */
public interface ModelSourceImagePort {

    /**
     * 소스 이미지를 일괄 저장한다.
     */
    List<ModelSourceImage> saveAll(List<ModelSourceImage> images);

    /**
     * 쇼케이스 ID 로 소스 이미지를 {@code sort_order} 오름차순으로 조회한다.
     */
    List<ModelSourceImage> findByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID 로 소스 이미지 개수를 조회한다.
     */
    int countByShowcaseId(Long showcaseId);

    /**
     * 쇼케이스 ID 로 소스 이미지 저장 URL 목록을 {@code sort_order} 오름차순으로 조회한다.
     * Tripo 업로드 / Worker TX1 S3 존재 검증에서 사용.
     */
    List<String> findImageUrlsByShowcaseId(Long showcaseId);
}
