package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정.
 */
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, ModelGenerationRequestMessage> modelGenerationRequestConsumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "model-generation-worker");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gearshow.backend.showcase.adapter.out.messaging.dto");
        // Outbox Relay 가 byte[] 로 raw JSON 을 전송하므로 type header 를 사용하지 않는다.
        // 기본 역직렬화 타겟 타입을 명시적으로 지정하여 고정된 POJO 로 변환한다.
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ModelGenerationRequestMessage.class.getName());
        // 메시지 유실 방지: offset이 없으면 토픽 처음부터 읽음
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Worker 는 createTask 직후 반환되어 블로킹이 수 초 이내로 짧아졌지만,
        // 네트워크/DB 일시 지연에 대한 여유로 10분을 유지한다.
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ModelGenerationRequestMessage>
            modelGenerationRequestListenerFactory(
                    DefaultErrorHandler modelGenerationRequestErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ModelGenerationRequestMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(modelGenerationRequestConsumerFactory());
        factory.setConcurrency(3);
        factory.setCommonErrorHandler(modelGenerationRequestErrorHandler);
        return factory;
    }

    /**
     * DLT publishing 용 KafkaTemplate 은 Outbox Relay 의 {@code outboxKafkaTemplate}
     * ({@code KafkaTemplate<String, byte[]>}) 을 재사용한다.
     * Spring Kafka 의 {@code DeadLetterPublishingRecoverer} 는 KafkaOperations 타입만 요구하므로
     * 제네릭 타입이 일치하지 않아도 무방하다.
     */
    @Bean
    public DefaultErrorHandler modelGenerationRequestErrorHandler(
            KafkaOperations<String, byte[]> outboxKafkaTemplate) {
        // 실패한 원본 메시지를 {originalTopic}.DLT 의 동일 파티션으로 라우팅한다.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                outboxKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        // 1초 간격으로 3번 재시도 후 DLT 로 이동.
        // 폴링 분리 아키텍처에선 Worker 가 수 초 내 반환하므로 재시도 간격을 짧게 유지해도 충분.
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
