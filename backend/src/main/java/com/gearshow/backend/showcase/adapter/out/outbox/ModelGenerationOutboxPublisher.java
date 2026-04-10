package com.gearshow.backend.showcase.adapter.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.platform.outbox.application.exception.OutboxEventSerializationException;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ModelGenerationEventPublisher} 의 Outbox 기반 구현체.
 *
 * <p>adapter 레이어에 위치하므로 Kafka DTO({@link ModelGenerationRequestMessage}),
 * ObjectMapper, Kafka 토픽 상수 같은 인프라 세부사항을 직접 참조한다.
 * Application 계층은 이 구현체를 알지 않으며, 오직 포트 인터페이스만 주입받는다.</p>
 *
 * <p>이벤트 발행 흐름:</p>
 * <ol>
 *   <li>{@link ModelGenerationRequestMessage} 페이로드 생성 (messageId UUID 포함)</li>
 *   <li>Jackson 으로 JSON 직렬화</li>
 *   <li>{@link OutboxMessage} 도메인 객체 생성 후 Outbox 테이블에 저장</li>
 * </ol>
 *
 * <p>실제 Kafka 로의 발행은 별도 {@code OutboxRelayService} 가 비동기로 수행한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ModelGenerationOutboxPublisher implements ModelGenerationEventPublisher {

    private static final String AGGREGATE_TYPE = "SHOWCASE_3D_MODEL";
    private static final String EVENT_TYPE = "MODEL_GENERATION_REQUESTED";

    private final OutboxMessagePort outboxMessagePort;
    private final ObjectMapper objectMapper;

    @Override
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
            throw new OutboxEventSerializationException(e);
        }
    }
}
