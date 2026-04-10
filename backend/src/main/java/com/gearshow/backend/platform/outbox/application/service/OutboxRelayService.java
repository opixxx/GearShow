package com.gearshow.backend.platform.outbox.application.service;

import com.gearshow.backend.platform.outbox.application.port.in.PublishOutboxUseCase;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessageBroker;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessageBroker.BrokerPublishException;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.platform.outbox.infrastructure.config.OutboxRelayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Outbox → Kafka Relay 서비스.
 *
 * <p>스케줄러가 주기적으로 호출한다. 아직 발행되지 않은 Outbox 메시지를 배치로 조회하여
 * {@link OutboxMessageBroker} 를 통해 발행하고, 성공 시 published 상태로 마킹한다.</p>
 *
 * <p><b>application 계층의 프레임워크 격리</b>: 이 서비스는 {@code KafkaTemplate} 같은
 * 인프라 API 에 직접 의존하지 않는다. {@link OutboxMessageBroker} 포트 뒤에 Kafka 세부사항이
 * 숨겨져 있어 메시지 브로커 교체(RabbitMQ, SNS 등) 시 application 코드 변경이 없다.</p>
 *
 * <p>발행 실패는 예외를 던지지 않고 로그만 남긴다. 실패한 메시지는 published=false 로
 * 남아있으므로 다음 주기에 자연스럽게 재시도된다.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxRelayService implements PublishOutboxUseCase {

    private final OutboxMessagePort outboxMessagePort;
    private final OutboxMessageBroker outboxMessageBroker;
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
     * 단일 메시지를 브로커로 발행하고 성공 시 published 로 마킹한다.
     *
     * @return 발행에 성공하면 {@code true}
     */
    private boolean publishSingle(OutboxMessage message) {
        byte[] payloadBytes = message.getPayload().getBytes(StandardCharsets.UTF_8);
        try {
            outboxMessageBroker.publish(
                    message.getTopic(),
                    message.getPartitionKey(),
                    payloadBytes,
                    properties.publishTimeoutMs());
            outboxMessagePort.markPublished(message.getId());
            log.debug("Outbox 메시지 발행 성공 - id: {}, topic: {}",
                    message.getId(), message.getTopic());
            return true;
        } catch (BrokerPublishException e) {
            // 다음 주기에 자연스럽게 재시도됨
            log.warn("Outbox 메시지 발행 실패 - id: {}, topic: {}, 다음 주기에 재시도",
                    message.getId(), message.getTopic(), e);
            return false;
        }
    }
}
