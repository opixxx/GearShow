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
    /** 3D 모델 생성 요청 DLT (재시도 소진된 메시지 격리) */
    public static final String MODEL_GENERATION_REQUEST_DLT = MODEL_GENERATION_REQUEST_TOPIC + ".DLT";

    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationRequestTopic() {
        return TopicBuilder.name(MODEL_GENERATION_REQUEST_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Dead Letter Topic — {@code DefaultErrorHandler} 의 재시도가 모두 소진된 메시지가 이동한다.
     * 운영 중 수동 확인/재처리 대상이다.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
    public NewTopic modelGenerationRequestDlt() {
        return TopicBuilder.name(MODEL_GENERATION_REQUEST_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
