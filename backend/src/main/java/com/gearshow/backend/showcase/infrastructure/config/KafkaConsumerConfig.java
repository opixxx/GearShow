package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

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
        // 메시지 유실 방지: offset이 없으면 토픽 처음부터 읽음
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        // Tripo 폴링 등 장시간 처리 대비: 10분간 poll 미호출 허용 (기본 5분)
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600000);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ModelGenerationRequestMessage>
            modelGenerationRequestListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ModelGenerationRequestMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(modelGenerationRequestConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }
}
