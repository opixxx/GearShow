package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import java.time.Instant;

/**
 * 3D 모델 생성 결과 메시지.
 * 워커가 생성 완료/실패 후 Kafka를 통해 전달한다.
 */
public record ModelGenerationResultMessage(
        Long showcase3dModelId,
        Long showcaseId,
        boolean success,
        String modelFileUrl,
        String previewImageUrl,
        String failureReason,
        Instant completedAt
) {

    public static ModelGenerationResultMessage success(Long showcase3dModelId, Long showcaseId,
                                                        String modelFileUrl, String previewImageUrl) {
        return new ModelGenerationResultMessage(
                showcase3dModelId, showcaseId, true,
                modelFileUrl, previewImageUrl, null, Instant.now());
    }

    public static ModelGenerationResultMessage failure(Long showcase3dModelId, Long showcaseId,
                                                        String failureReason) {
        return new ModelGenerationResultMessage(
                showcase3dModelId, showcaseId, false,
                null, null, failureReason, Instant.now());
    }
}
