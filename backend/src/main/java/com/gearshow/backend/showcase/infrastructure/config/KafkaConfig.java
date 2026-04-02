package com.gearshow.backend.showcase.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽 설정.
 */
@Configuration
public class KafkaConfig {

    /** 3D 모델 생성 요청 토픽 */
    public static final String MODEL_GENERATION_REQUEST_TOPIC = "showcase.model-generation.request";
    /** 3D 모델 생성 결과 토픽 */
    public static final String MODEL_GENERATION_RESULT_TOPIC = "showcase.model-generation.result";

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationRequestTopic() {
        return TopicBuilder.name(MODEL_GENERATION_REQUEST_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationResultTopic() {
        return TopicBuilder.name(MODEL_GENERATION_RESULT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
