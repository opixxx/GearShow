package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tripo 폴링 스케줄러 설정.
 *
 * @param intervalMs       스케줄러 주기(ms)
 * @param batchSize        한 번의 폴링 사이클에서 처리할 최대 모델 수
 * @param taskTimeoutMinutes 폴링 중인 task 가 이 시간을 초과하면 자동 FAILED 처리
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
        int taskTimeoutMinutes
) {
}
