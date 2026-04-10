package com.gearshow.backend.platform.outbox.application.service;

import com.gearshow.backend.platform.outbox.application.port.in.CleanupOutboxUseCase;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.infrastructure.config.OutboxRelayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 발행 완료된 Outbox 메시지 정리 서비스.
 *
 * <p>인덱스 락 경합 최소화를 위해 배치 단위로 삭제하고 배치 사이에 sleep 을 둔다.
 * 정리 스케줄러가 주기적으로 이 서비스를 호출한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupOutboxService implements CleanupOutboxUseCase {

    private final OutboxMessagePort outboxMessagePort;
    private final OutboxRelayProperties properties;

    @Override
    public int cleanup() {
        Instant threshold = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        int totalDeleted = 0;
        int batchSize = properties.cleanupBatchSize();

        while (true) {
            int deleted = outboxMessagePort.deletePublishedBatchOlderThan(threshold, batchSize);
            totalDeleted += deleted;

            if (deleted < batchSize) {
                break;
            }
            if (!sleepBetweenBatches()) {
                break;
            }
        }

        if (totalDeleted > 0) {
            log.info("Outbox 메시지 정리 완료 - 삭제 행 수: {}", totalDeleted);
        }
        return totalDeleted;
    }

    /**
     * 배치 사이 sleep. 인터럽트 수신 시 false 를 반환하여 루프 종료를 유도한다.
     */
    private boolean sleepBetweenBatches() {
        long sleepMs = properties.cleanupBatchSleepMs();
        if (sleepMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(sleepMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Outbox 정리 배치 sleep 인터럽트 - 루프 종료");
            return false;
        }
    }
}
