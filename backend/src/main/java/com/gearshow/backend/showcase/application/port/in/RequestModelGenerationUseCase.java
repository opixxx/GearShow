package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;

import java.util.List;

/**
 * 3D 모델 생성 요청 유스케이스.
 */
public interface RequestModelGenerationUseCase {

    /**
     * 쇼케이스 등록 시 3D 모델 생성을 비동기로 요청한다.
     * 이미지는 클라이언트가 Presigned URL로 S3에 직접 업로드하고,
     * 서버는 S3 키 목록을 전달받아 DB에 저장한다.
     *
     * @param showcaseId           쇼케이스 ID
     * @param modelSourceImageKeys 소스 이미지 S3 키 목록 (앞/뒤/좌/우, 최소 4개)
     * @return 생성 요청 결과
     */
    ModelGenerationResult requestOnCreate(Long showcaseId, List<String> modelSourceImageKeys);

    /**
     * 3D 모델 생성을 재요청한다 (실패 후 재시도).
     *
     * @param showcaseId           쇼케이스 ID
     * @param ownerId              요청자 ID
     * @param modelSourceImageKeys 소스 이미지 S3 키 목록 (최소 4개)
     * @return 생성 요청 결과
     */
    ModelGenerationResult requestRetry(Long showcaseId, Long ownerId, List<String> modelSourceImageKeys);
}
