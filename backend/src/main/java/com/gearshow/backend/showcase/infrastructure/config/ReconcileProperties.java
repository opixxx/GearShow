package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Reconcile 배치 설정 (설계 §8.4 임계값).
 *
 * @param enabled                       스케줄러 활성 여부. 기본 비활성.
 * @param fixedDelayMs                  스케줄러 실행 주기. 기본 60초.
 * @param batchSize                     한 사이클에서 카테고리별 조회 최대 건수.
 * @param preparingStuckSeconds         PREPARING heartbeat 갭 임계.
 * @param generatingTripoStuckMinutes   GENERATING + tripo_succeeded_at IS NULL + last_polled_at 갭 임계.
 * @param generatingS3StuckMinutes      GENERATING + tripo_succeeded_at IS NOT NULL + heartbeat_at 갭 임계.
 * @param requestedStuckSeconds         REQUESTED 경고 임계 (Outbox Relay 점검 신호).
 */
@Validated
@ConfigurationProperties(prefix = "app.reconcile")
public record ReconcileProperties(
        @DefaultValue("false")
        boolean enabled,

        @Min(value = 1000, message = "fixedDelayMs 는 1000 이상이어야 합니다")
        @DefaultValue("60000")
        long fixedDelayMs,

        @Min(value = 1, message = "batchSize 는 1 이상이어야 합니다")
        @DefaultValue("50")
        int batchSize,

        @Min(value = 5, message = "preparingStuckSeconds 는 5 이상이어야 합니다")
        @DefaultValue("60")
        long preparingStuckSeconds,

        @Min(value = 1, message = "generatingTripoStuckMinutes 는 1 이상이어야 합니다")
        @DefaultValue("8")
        long generatingTripoStuckMinutes,

        @Min(value = 1, message = "generatingS3StuckMinutes 는 1 이상이어야 합니다")
        @DefaultValue("5")
        long generatingS3StuckMinutes,

        @Min(value = 5, message = "requestedStuckSeconds 는 5 이상이어야 합니다")
        @DefaultValue("30")
        long requestedStuckSeconds
) {
}
