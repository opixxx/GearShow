package com.gearshow.backend.platform.outbox.adapter.out.kafka;

import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessageBroker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link OutboxMessageBroker} 의 Kafka 기반 구현체.
 *
 * <p>{@code KafkaTemplate<String, byte[]>} 를 사용해 raw JSON 바이트를 발행한다.
 * application 계층이 Kafka SDK 에 직접 의존하지 않도록 이 어댑터가 모든 Kafka 관련
 * 세부사항을 격리한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaOutboxMessageBroker implements OutboxMessageBroker {

    private final KafkaTemplate<String, byte[]> outboxKafkaTemplate;

    @Override
    public void publish(String topic, String partitionKey, byte[] payload, long timeoutMs) {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, partitionKey, payload);
        try {
            SendResult<String, byte[]> result = outboxKafkaTemplate.send(record)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
            log.debug("Outbox 메시지 Kafka 발행 성공 - topic: {}, partition: {}, offset: {}",
                    topic,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BrokerPublishException(
                    "Outbox 메시지 발행 중 인터럽트 - topic: " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new BrokerPublishException(
                    "Outbox 메시지 Kafka 발행 실패 - topic: " + topic, e);
        }
    }
}
