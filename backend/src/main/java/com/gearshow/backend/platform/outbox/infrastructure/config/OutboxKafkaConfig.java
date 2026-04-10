package com.gearshow.backend.platform.outbox.infrastructure.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Outbox Relay 용 Kafka Producer 설정.
 *
 * <p>Outbox 테이블에는 페이로드가 이미 JSON 문자열로 저장되어 있으므로,
 * Relay 는 바이트 그대로 Kafka 에 전송한다. 도메인별 POJO 직렬화기를 거치지 않고
 * byte 를 직접 보내기 위해 별도 Producer 를 구성한다.</p>
 *
 * <p>신뢰성 설정(acks=all, idempotence, retries)은 비즈니스 Producer 와 동일.</p>
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class OutboxKafkaConfig {

    private final String bootstrapServers;

    /**
     * 생성자 주입. {@code @Value} 필드 주입은 테스트 용이성과 DI 일관성 관점에서 지양한다.
     */
    public OutboxKafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, byte[]> outboxProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        // 메시지 유실 방지: 모든 ISR 가 복제 확인해야 ack
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Producer 멱등성: 재시도 중복 발행 방지
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // 일시적 오류 시 자동 재시도
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, byte[]> outboxKafkaTemplate() {
        return new KafkaTemplate<>(outboxProducerFactory());
    }
}
