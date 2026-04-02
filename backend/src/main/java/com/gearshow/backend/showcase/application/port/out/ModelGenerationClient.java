package com.gearshow.backend.showcase.application.port.out;

/**
 * 3D 모델 생성 외부 클라이언트 Port.
 * 실제 3D 모델 생성 API를 호출하거나 Fake 구현을 사용한다.
 */
public interface ModelGenerationClient {

    /**
     * 3D 모델을 생성한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @param showcaseId        쇼케이스 ID
     * @return 생성 결과
     */
    GenerationResult generate(Long showcase3dModelId, Long showcaseId);

    /**
     * 3D 모델 생성 결과.
     */
    record GenerationResult(
            boolean success,
            String modelFileUrl,
            String previewImageUrl,
            String failureReason
    ) {
        public static GenerationResult success(String modelFileUrl, String previewImageUrl) {
            return new GenerationResult(true, modelFileUrl, previewImageUrl, null);
        }

        public static GenerationResult failure(String failureReason) {
            return new GenerationResult(false, null, null, failureReason);
        }
    }
}
