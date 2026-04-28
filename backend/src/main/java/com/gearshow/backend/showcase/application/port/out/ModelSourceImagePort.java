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

    /**
     * 쇼케이스 ID 로 소스 이미지를 모두 hard delete 한다.
     *
     * <p>재시도 시 옛 소스 이미지를 정리하고 새 4장으로 갈아끼울 때 사용한다. attempt 별 이력은
     * {@code model_generation_workflow} 가 보존하므로 이 테이블은 "현재 최신 4장" 만 유지한다.</p>
     */
    void deleteByShowcaseId(Long showcaseId);
}
