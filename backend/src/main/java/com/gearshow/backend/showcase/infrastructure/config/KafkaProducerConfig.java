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
 *
 * <p><b>메시지 유실 방지 설정 요약</b></p>
 * <ul>
 *     <li>{@code acks=all} : 모든 ISR이 복제 확인해야 ack를 반환한다.</li>
 *     <li>{@code enable.idempotence=true} : Producer 측 재시도 중복을 브로커가 제거한다.</li>
 *     <li><b>브로커 측</b> {@code min.insync.replicas=2} : {@code acks=all}과 짝을 이뤄
 *         실질적 내구성을 보장한다. 이 값은 브로커/토픽 설정으로만 적용 가능하므로
 *         클라이언트 쪽에서는 설정할 수 없다. 운영 브로커(MSK 등) 도입 시 토픽 생성
 *         옵션 또는 {@code server.properties}에서 반드시 {@code >= 2}로 설정해야 하며,
 *         그렇지 않으면 단일 브로커/단일 replica 환경에서는 {@code acks=all}이 사실상
 *         {@code acks=1}과 동일하게 동작한다.</li>
 * </ul>
 *
 * <p>현 단계는 단일 브로커(개발용) 환경이므로 {@code min.insync.replicas}는
 * 운영 전환 시 토픽 레벨에서 설정한다.</p>
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
        // (짝이 되는 브로커 설정: min.insync.replicas >= 2, 운영 전환 시 필수)
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
