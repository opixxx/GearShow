package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, ModelGenerationRequestMessage> modelGenerationRequestProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // 메시지 유실 방지: 모든 ISR이 복제 확인해야 ack 반환
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Producer 멱등성: 네트워크 재시도로 인한 브로커 측 중복 발행 방지
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // 일시적 오류 시 자동 재시도
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, ModelGenerationRequestMessage> kafkaTemplate() {
        return new KafkaTemplate<>(modelGenerationRequestProducerFactory());
    }
}
