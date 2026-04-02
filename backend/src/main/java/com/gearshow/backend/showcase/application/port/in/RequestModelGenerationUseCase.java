package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.UploadFile;

import java.util.List;

/**
 * 3D 모델 생성 요청 유스케이스.
 */
public interface RequestModelGenerationUseCase {

    /**
     * 쇼케이스 등록 시 3D 모델 생성을 비동기로 요청한다.
     *
     * @param showcaseId        쇼케이스 ID
     * @param modelSourceImages 소스 이미지 파일 목록 (앞/뒤/좌/우)
     * @return 생성 요청 결과
     */
    ModelGenerationResult requestOnCreate(Long showcaseId, List<UploadFile> modelSourceImages);

    /**
     * 3D 모델 생성을 재요청한다 (실패 후 재시도).
     *
     * @param showcaseId        쇼케이스 ID
     * @param ownerId           요청자 ID
     * @param modelSourceImages 소스 이미지 파일 목록
     * @return 생성 요청 결과
     */
    ModelGenerationResult requestRetry(Long showcaseId, Long ownerId, List<UploadFile> modelSourceImages);
}
