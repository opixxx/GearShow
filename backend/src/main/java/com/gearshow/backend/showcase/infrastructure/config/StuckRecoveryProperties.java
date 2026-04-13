package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 좀비 모델 복구 스케줄러 설정.
 *
 * @param intervalMs                          스케줄러 주기(ms)
 * @param batchSize                           한 사이클에서 처리할 최대 모델 수
 * @param requestedStuckMinutes               REQUESTED 상태가 이 시간 이상 지속되면 Outbox 재등록 대상
 * @param preparingStuckMinutes               PREPARING 상태가 이 시간 이상 지속되면 자동 재시도 또는 FAILED
 * @param generatingWithoutTaskIdStuckMinutes GENERATING 상태 + task_id 없음이 이 시간 이상 지속되면 FAILED
 */
@Validated
@ConfigurationProperties(prefix = "app.stuck-recovery")
public record StuckRecoveryProperties(
        @Min(value = 1000, message = "intervalMs 는 1000 이상이어야 합니다")
        @DefaultValue("60000")
        long intervalMs,

        @Min(value = 1, message = "batchSize 는 1 이상이어야 합니다")
        @DefaultValue("50")
        int batchSize,

        @Min(value = 1, message = "requestedStuckMinutes 는 1 이상이어야 합니다")
        @DefaultValue("5")
        int requestedStuckMinutes,

        @Min(value = 1, message = "preparingStuckMinutes 는 1 이상이어야 합니다")
        @DefaultValue("2")
        int preparingStuckMinutes,

        @Min(value = 1, message = "generatingWithoutTaskIdStuckMinutes 는 1 이상이어야 합니다")
        @DefaultValue("5")
        int generatingWithoutTaskIdStuckMinutes
) {
}
