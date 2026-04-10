package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import com.gearshow.backend.platform.idempotency.application.port.out.ProcessedMessagePort;
import com.gearshow.backend.platform.idempotency.infrastructure.config.IdempotencyCleanupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 처리된 메시지 이력 정리 유스케이스 구현체.
 *
 * <p>보존 기간(기본 7일)이 지난 이력을 배치 단위(기본 1,000행)로 분할 삭제한다.
 * 한 번에 모든 행을 삭제하면 인덱스 락 경합으로 동시 INSERT가 멈출 수 있으므로
 * 배치 사이에 짧은 sleep을 두어 다른 트랜잭션에 양보한다.</p>
 *
 * <p>설정값은 {@link IdempotencyCleanupProperties}에서 주입되며,
 * Bean 생성 시점에 {@code @Validated}로 유효성이 검증되므로
 * {@code batchSize=0} 같은 잘못된 값으로 무한 루프가 발생할 수 없다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupProcessedMessageService implements CleanupProcessedMessageUseCase {

    private final ProcessedMessagePort processedMessagePort;
    private final IdempotencyCleanupProperties properties;

    @Override
    public int cleanup() {
        Instant threshold = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
        int batchSize = properties.cleanupBatchSize();
        int totalDeleted = 0;

        while (true) {
            int deleted = processedMessagePort.deleteBatchOlderThan(threshold, batchSize);
            totalDeleted += deleted;

            if (deleted < batchSize) {
                break;
            }

            // 인터럽트가 발생하면 즉시 루프를 종료하여 운영 중단 신호에 응답한다
            if (!sleepBetweenBatches()) {
                log.warn("배치 정리 루프가 인터럽트로 조기 종료됨 - 삭제 건수: {}", totalDeleted);
                break;
            }
        }

        log.info("처리된 메시지 이력 정리 완료 - 삭제 건수: {}, 기준 시각: {}",
                totalDeleted, threshold);
        return totalDeleted;
    }

    /**
     * 배치 사이에 짧게 휴식하여 다른 트랜잭션이 진입할 여지를 만든다.
     *
     * @return 정상 슬립 완료 시 {@code true}, 인터럽트로 중단 시 {@code false}
     */
    private boolean sleepBetweenBatches() {
        try {
            Thread.sleep(properties.cleanupBatchSleepMs());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
