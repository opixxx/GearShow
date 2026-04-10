package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 3D 모델 생성 요청 메시지.
 * Kafka를 통해 워커에게 전달된다.
 *
 * @param messageId        멱등성 보장을 위한 메시지 고유 식별자 (UUID)
 * @param showcase3dModelId 3D 모델 ID
 * @param showcaseId       쇼케이스 ID
 * @param requestedAt      요청 시각
 */
public record ModelGenerationRequestMessage(
        String messageId,
        Long showcase3dModelId,
        Long showcaseId,
        Instant requestedAt
) {

    public static ModelGenerationRequestMessage of(Long showcase3dModelId, Long showcaseId) {
        return new ModelGenerationRequestMessage(
                UUID.randomUUID().toString(),
                showcase3dModelId,
                showcaseId,
                Instant.now()
        );
    }
}
