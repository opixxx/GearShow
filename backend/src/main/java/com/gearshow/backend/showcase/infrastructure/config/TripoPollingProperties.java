package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tripo 폴링 스케줄러 설정 (P1-E 이후 DelayedQueue 기반 경로 전용).
 *
 * <p><b>ADR-010 / P1-G 정리</b>: 레거시 {@code @Scheduled} 기반 경로가 제거되면서
 * {@code intervalMs} / {@code batchSize} / {@code taskTimeoutMinutes} 필드도 삭제했다. 현재는
 * DelayedQueue 재대기 지연과 세마포어 획득 타임아웃만 구성한다.</p>
 *
 * @param delaySeconds               Worker/Poller 가 DelayedQueue 에 re-offer 할 기본 지연(초). 설계 §6.2 30초.
 * @param semaphoreAcquireTimeoutMs  Tripo semaphore permit 획득 대기 상한(ms).
 * @param schedulerEnabled           {@link com.gearshow.backend.showcase.adapter.in.scheduler.WorkflowPollingScheduler}
 *                                   구동 여부. 기본 활성.
 */
@Validated
@ConfigurationProperties(prefix = "app.tripo-polling")
public record TripoPollingProperties(
        @Min(value = 1, message = "delaySeconds 는 1 이상이어야 합니다")
        @DefaultValue("30")
        int delaySeconds,

        @Min(value = 100, message = "semaphoreAcquireTimeoutMs 는 100 이상이어야 합니다")
        @DefaultValue("2000")
        long semaphoreAcquireTimeoutMs,

        @DefaultValue("true")
        boolean schedulerEnabled
) {
}
