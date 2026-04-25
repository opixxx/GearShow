package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

/**
 * 3D 모델 생성 요청 메시지.
 * Kafka 를 통해 워커에게 전달된다.
 *
 * <p><b>스키마 하위 호환</b>: {@code ignoreUnknown=true} 로 설정하여 Producer 가 새 필드를 추가해도
 * Consumer 가 역직렬화 실패하지 않는다. 또한 ADR-010 직후 cut over 단계에서 큐에 옛 스키마
 * (`showcase3dModelId` 포함) 가 남아있을 수 있는데, 같은 옵션 덕분에 Consumer 가 무시한다.</p>
 *
 * <p><b>messageId</b>: P1-B-γ 이후 {@code SHA-256(Idempotency-Key)} hex 64 자 (ADR-011 ③).
 * Consumer 의 {@code processed_message.event_id} 중복 차단과 Outbox {@code event_id} UNIQUE
 * 제약을 함께 만족시킨다.</p>
 *
 * @param messageId   멱등성 보장을 위한 결정적 식별자 (SHA-256 hex 64자)
 * @param workflowId  {@code model_generation_workflow} 행 ID (ADR-010)
 * @param showcaseId  쇼케이스 ID (파티션 키 + Outbox aggregate ID)
 * @param requestedAt 요청 시각
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelGenerationRequestMessage(
        String messageId,
        Long workflowId,
        Long showcaseId,
        Instant requestedAt
) {

    public ModelGenerationRequestMessage {
        Objects.requireNonNull(messageId, "messageId는 필수입니다");
        Objects.requireNonNull(workflowId, "workflowId는 필수입니다");
        Objects.requireNonNull(showcaseId, "showcaseId는 필수입니다");
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다");
    }

    public static ModelGenerationRequestMessage of(String messageId,
                                                   Long workflowId,
                                                   Long showcaseId) {
        return new ModelGenerationRequestMessage(messageId, workflowId, showcaseId, Instant.now());
    }
}
