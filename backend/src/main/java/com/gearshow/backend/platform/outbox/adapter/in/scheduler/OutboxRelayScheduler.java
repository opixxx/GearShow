package com.gearshow.backend.platform.outbox.adapter.in.scheduler;

import com.gearshow.backend.platform.outbox.application.port.in.PublishOutboxUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox → Kafka Relay 스케줄러 (Inbound Adapter).
 *
 * <p>주기는 {@code app.outbox.relay-interval-ms} 로 설정한다 (기본 1초).
 * Kafka 가 비활성화된 환경에서는 빈 자체가 생성되지 않아 스케줄러도 동작하지 않는다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final PublishOutboxUseCase publishOutboxUseCase;

    @Scheduled(fixedDelayString = "${app.outbox.relay-interval-ms:1000}")
    public void relay() {
        try {
            int published = publishOutboxUseCase.publishPending();
            if (published > 0) {
                log.debug("Outbox Relay 실행 완료 - 발행 수: {}", published);
            }
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않도록 예외를 포착하여 로그만 남긴다.
            log.error("Outbox Relay 실행 중 예외 발생", e);
        }
    }
}
