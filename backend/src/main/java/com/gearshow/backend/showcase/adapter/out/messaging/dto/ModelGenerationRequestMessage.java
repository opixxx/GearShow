package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import java.time.Instant;

/**
 * 3D 모델 생성 요청 메시지.
 * Kafka를 통해 워커에게 전달된다.
 */
public record ModelGenerationRequestMessage(
        Long showcase3dModelId,
        Long showcaseId,
        Instant requestedAt
) {

    public static ModelGenerationRequestMessage of(Long showcase3dModelId, Long showcaseId) {
        return new ModelGenerationRequestMessage(showcase3dModelId, showcaseId, Instant.now());
    }
}
