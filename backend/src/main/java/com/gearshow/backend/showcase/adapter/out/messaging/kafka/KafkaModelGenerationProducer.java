package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationPort;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 기반 3D 모델 생성 요청 Producer.
 * ModelGenerationPort를 구현하여 비동기 메시지를 발행한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaModelGenerationProducer implements ModelGenerationPort {

    private final KafkaTemplate<String, ModelGenerationRequestMessage> kafkaTemplate;

    @Override
    public void requestGeneration(Long showcase3dModelId, Long showcaseId) {
        ModelGenerationRequestMessage message = ModelGenerationRequestMessage.of(
                showcase3dModelId, showcaseId);

        kafkaTemplate.send(KafkaConfig.MODEL_GENERATION_REQUEST_TOPIC,
                        String.valueOf(showcaseId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("3D 모델 생성 요청 메시지 발행 실패 - showcase3dModelId: {}, showcaseId: {}",
                                showcase3dModelId, showcaseId, ex);
                    } else {
                        log.info("3D 모델 생성 요청 메시지 발행 성공 - showcase3dModelId: {}, showcaseId: {}, offset: {}",
                                showcase3dModelId, showcaseId,
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
