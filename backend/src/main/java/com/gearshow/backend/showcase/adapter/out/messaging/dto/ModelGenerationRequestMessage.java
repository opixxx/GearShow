package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 3D 모델 생성 요청 메시지.
 * Kafka를 통해 워커에게 전달된다.
 *
 * <p><b>설계 결정 #6 (메시지 스키마 하위 호환)</b>:
 * {@code ignoreUnknown=true} 로 설정하여 Producer 가 새 필드를 추가해도
 * Consumer 가 역직렬화 실패하지 않게 한다. 서버 분리 후 배포 시점 차이에 의한
 * poison pill 문제를 방지한다.</p>
 *
 * @param messageId        멱등성 보장을 위한 메시지 고유 식별자 (UUID)
 * @param showcase3dModelId 3D 모델 ID
 * @param showcaseId       쇼케이스 ID
 * @param requestedAt      요청 시각
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelGenerationRequestMessage(
        String messageId,
        Long showcase3dModelId,
        Long showcaseId,
        Instant requestedAt
) {

    /**
     * 레코드 생성 시점에 필수 필드 불변식을 강제한다.
     * Kafka 직렬화/역직렬화 및 팩토리 메서드 양쪽 모두에서 null 주입을 차단한다.
     */
    public ModelGenerationRequestMessage {
        Objects.requireNonNull(messageId, "messageId는 필수입니다");
        Objects.requireNonNull(showcase3dModelId, "showcase3dModelId는 필수입니다");
        Objects.requireNonNull(showcaseId, "showcaseId는 필수입니다");
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다");
    }

    public static ModelGenerationRequestMessage of(Long showcase3dModelId, Long showcaseId) {
        return new ModelGenerationRequestMessage(
                UUID.randomUUID().toString(),
                showcase3dModelId,
                showcaseId,
                Instant.now()
        );
    }
}
