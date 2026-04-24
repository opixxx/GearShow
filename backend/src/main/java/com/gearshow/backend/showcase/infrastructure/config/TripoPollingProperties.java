package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tripo 폴링 스케줄러 설정.
 *
 * @param intervalMs                 레거시 {@code @Scheduled} 기반 스케줄러 주기(ms).
 *                                   P1-E 이후 Redis DelayedQueue 기반 Poller 로 대체되며
 *                                   레거시 경로가 제거되면 함께 제거 예정.
 * @param batchSize                  레거시 스케줄러가 한 사이클에 처리할 최대 모델 수
 * @param taskTimeoutMinutes         폴링 중인 task 가 이 시간을 초과하면 자동 FAILED 처리
 * @param delaySeconds               Worker/Poller 가 DelayedQueue 에 re-offer 할 기본 지연(초).
 *                                   설계 §6.2 30초.
 * @param semaphoreAcquireTimeoutMs  Tripo semaphore permit 획득 대기 상한(ms).
 *                                   초과 시 {@code TripoSemaphoreTimeoutException} 으로 재시도 유도.
 */
@Validated
@ConfigurationProperties(prefix = "app.tripo-polling")
public record TripoPollingProperties(
        @Min(value = 500, message = "intervalMs 는 500 이상이어야 합니다")
        @DefaultValue("3000")
        long intervalMs,

        @Min(value = 1, message = "batchSize 는 1 이상이어야 합니다")
        @DefaultValue("20")
        int batchSize,

        @Min(value = 1, message = "taskTimeoutMinutes 는 1 이상이어야 합니다")
        @DefaultValue("15")
        int taskTimeoutMinutes,

        @Min(value = 1, message = "delaySeconds 는 1 이상이어야 합니다")
        @DefaultValue("30")
        int delaySeconds,

        @Min(value = 100, message = "semaphoreAcquireTimeoutMs 는 100 이상이어야 합니다")
        @DefaultValue("2000")
        long semaphoreAcquireTimeoutMs
) {
}
