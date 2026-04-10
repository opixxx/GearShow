package com.gearshow.backend.platform.idempotency.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 처리된 메시지 이력 정리 설정.
 *
 * <p>Bean 생성 시점에 값이 검증되어 부적절한 설정이 런타임 루프에 도달하지 않는다.
 * (ex. {@code cleanupBatchSize=0}으로 무한 루프 발생 방지)</p>
 *
 * @param retentionDays      삭제 대상 보존 기간(일). 이 값보다 오래된 이력을 삭제한다.
 * @param cleanupBatchSize   한 번의 DELETE 배치로 삭제할 최대 행 수
 * @param cleanupBatchSleepMs 배치 사이 sleep(ms). 락 경합 완화를 위해 다른 트랜잭션에 양보한다.
 */
@Validated
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyCleanupProperties(
        @Min(value = 1, message = "retentionDays는 1 이상이어야 합니다")
        @DefaultValue("7")
        int retentionDays,

        @Min(value = 1, message = "cleanupBatchSize는 1 이상이어야 합니다")
        @DefaultValue("1000")
        int cleanupBatchSize,

        @Min(value = 0, message = "cleanupBatchSleepMs는 0 이상이어야 합니다")
        @DefaultValue("50")
        long cleanupBatchSleepMs
) {
}
