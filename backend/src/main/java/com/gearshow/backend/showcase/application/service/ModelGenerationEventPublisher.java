package com.gearshow.backend.showcase.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 3D 모델 생성 요청 이벤트를 Outbox 에 기록하는 퍼블리셔.
 *
 * <p>Facade / Service 의 DB 트랜잭션 안에서 호출되어 "DB 저장 + 이벤트 기록" 을
 * 원자적으로 커밋한다. 실제 Kafka 발행은 {@code OutboxRelayService} 가 담당.</p>
 *
 * <p>이 클래스가 ObjectMapper 에 대한 의존을 대신 떠맡음으로써,
 * 상위 Service 레이어는 직렬화 라이브러리에 대한 지식을 갖지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
public class ModelGenerationEventPublisher {

    private static final String AGGREGATE_TYPE = "SHOWCASE_3D_MODEL";
    private static final String EVENT_TYPE = "MODEL_GENERATION_REQUESTED";

    private final OutboxMessagePort outboxMessagePort;
    private final ObjectMapper objectMapper;

    /**
     * 3D 모델 생성 요청 이벤트를 Outbox 에 기록한다.
     *
     * @param showcase3dModelId 3D 모델 ID (aggregate 식별자)
     * @param showcaseId        쇼케이스 ID (파티션 키)
     */
    public void publishRequested(Long showcase3dModelId, Long showcaseId) {
        ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                showcase3dModelId, showcaseId);
        String payload = serialize(message);

        OutboxMessage outboxMessage = OutboxMessage.create(
                AGGREGATE_TYPE,
                showcase3dModelId,
                EVENT_TYPE,
                KafkaConfig.MODEL_GENERATION_REQUEST_TOPIC,
                String.valueOf(showcaseId),
                message.messageId(),
                payload
        );
        outboxMessagePort.save(outboxMessage);
    }

    private String serialize(ModelGenerationRequestMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // 도메인 DTO 의 직렬화가 실패하는 건 개발 시점 버그이므로 런타임에 즉시 터뜨린다.
            throw new IllegalStateException(
                    "ModelGenerationRequestMessage 직렬화 실패", e);
        }
    }
}
