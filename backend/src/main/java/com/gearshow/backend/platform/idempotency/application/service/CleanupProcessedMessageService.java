package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import com.gearshow.backend.platform.idempotency.application.port.out.ProcessedMessagePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 처리된 메시지 이력 정리 유스케이스 구현체.
 *
 * <p>보존 기간(기본 7일)이 지난 이력을 배치 단위(기본 1,000행)로 분할 삭제한다.
 * 한 번에 모든 행을 삭제하면 인덱스 락 경합으로 동시 INSERT가 멈출 수 있으므로
 * 배치 사이에 짧은 sleep을 두어 다른 트랜잭션에 양보한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupProcessedMessageService implements CleanupProcessedMessageUseCase {

    private final ProcessedMessagePort processedMessagePort;

    @Value("${app.idempotency.retention-days:7}")
    private int retentionDays;

    @Value("${app.idempotency.cleanup-batch-size:1000}")
    private int batchSize;

    @Value("${app.idempotency.cleanup-batch-sleep-ms:50}")
    private long batchSleepMs;

    @Override
    public int cleanup() {
        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int totalDeleted = 0;

        while (true) {
            int deleted = processedMessagePort.deleteBatchOlderThan(threshold, batchSize);
            totalDeleted += deleted;

            if (deleted < batchSize) {
                break;
            }

            sleepBetweenBatches();
        }

        log.info("처리된 메시지 이력 정리 완료 - 삭제 건수: {}, 기준 시각: {}",
                totalDeleted, threshold);
        return totalDeleted;
    }

    /**
     * 배치 사이에 짧게 휴식하여 다른 트랜잭션이 진입할 여지를 만든다.
     */
    private void sleepBetweenBatches() {
        try {
            Thread.sleep(batchSleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("배치 정리 중 인터럽트 발생, 정리 중단");
        }
    }
}
