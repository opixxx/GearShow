package com.gearshow.backend.platform.outbox.application.service;

import com.gearshow.backend.platform.outbox.application.port.in.PublishOutboxUseCase;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.platform.outbox.infrastructure.config.OutboxRelayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Outbox → Kafka Relay 서비스.
 *
 * <p>스케줄러가 주기적으로 호출한다. 아직 발행되지 않은 Outbox 메시지를 배치로 조회하여
 * Kafka 로 발행하고, 발행 성공 시 published 상태로 마킹한다.</p>
 *
 * <p>발행 실패는 예외를 던지지 않고 로그만 남긴다. 실패한 메시지는 published=false 로
 * 남아있으므로 다음 주기에 자연스럽게 재시도된다. 이로써 장애 시 손실 없이 복구된다.</p>
 *
 * <p>동기 발행 대기({@code get}) 를 사용하는 이유:
 * 비동기 fire-and-forget 로 보내면 콜백에서 DB 업데이트를 해야 하는데,
 * Spring 트랜잭션 컨텍스트가 호출 스레드에만 바인딩되기 때문에 콜백에서
 * 깔끔히 처리하기 어렵다. 동기 발행은 동일 스레드에서 DB 업데이트까지 처리할 수 있다.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxRelayService implements PublishOutboxUseCase {

    private final OutboxMessagePort outboxMessagePort;
    private final KafkaTemplate<String, byte[]> outboxKafkaTemplate;
    private final OutboxRelayProperties properties;

    @Override
    public int publishPending() {
        List<OutboxMessage> pending = outboxMessagePort.findPendingBatch(properties.batchSize());
        if (pending.isEmpty()) {
            return 0;
        }

        int publishedCount = 0;
        for (OutboxMessage message : pending) {
            if (publishSingle(message)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }

    /**
     * 단일 메시지를 Kafka 로 발행하고 성공 시 published 로 마킹한다.
     *
     * @return 발행에 성공하면 {@code true}
     */
    private boolean publishSingle(OutboxMessage message) {
        byte[] payloadBytes = message.getPayload().getBytes(StandardCharsets.UTF_8);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                message.getTopic(),
                message.getPartitionKey(),
                payloadBytes
        );

        try {
            SendResult<String, byte[]> result = outboxKafkaTemplate.send(record)
                    .get(properties.publishTimeoutMs(), TimeUnit.MILLISECONDS);
            outboxMessagePort.markPublished(message.getId());
            log.debug("Outbox 메시지 발행 성공 - id: {}, topic: {}, partition: {}, offset: {}",
                    message.getId(),
                    message.getTopic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Outbox 발행 중 인터럽트 - id: {}", message.getId());
            return false;
        } catch (ExecutionException | TimeoutException e) {
            // 다음 주기에 자연스럽게 재시도됨
            log.warn("Outbox 메시지 발행 실패 - id: {}, topic: {}, 다음 주기에 재시도",
                    message.getId(), message.getTopic(), e);
            return false;
        }
    }
}
