package com.gearshow.backend.platform.outbox.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Outbox Relay 스케줄러 설정.
 *
 * <p>Bean 생성 시점에 값이 검증되어 런타임에 잘못된 설정으로 인한
 * 무한 루프/과도 부하가 발생하지 않도록 한다.</p>
 *
 * @param relayIntervalMs      Relay 스케줄러 주기(ms)
 * @param batchSize            한 번에 처리할 최대 Outbox 메시지 수
 * @param publishTimeoutMs     Kafka 발행 ack 대기 타임아웃(ms)
 * @param cleanupCron          발행 완료 레코드 정리 cron 식
 * @param cleanupZone          cleanupCron 의 타임존
 * @param retentionDays        발행 완료 레코드 보존 기간(일)
 * @param cleanupBatchSize     한 번의 DELETE 배치 크기
 * @param cleanupBatchSleepMs  배치 사이 sleep(ms)
 */
@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxRelayProperties(
        @Min(value = 100, message = "relayIntervalMs 는 100 이상이어야 합니다")
        @DefaultValue("1000")
        long relayIntervalMs,

        @Min(value = 1, message = "batchSize 는 1 이상이어야 합니다")
        @DefaultValue("100")
        int batchSize,

        @Min(value = 100, message = "publishTimeoutMs 는 100 이상이어야 합니다")
        @DefaultValue("5000")
        long publishTimeoutMs,

        @DefaultValue("0 0 4 * * *")
        String cleanupCron,

        @DefaultValue("Asia/Seoul")
        String cleanupZone,

        @Min(value = 1, message = "retentionDays 는 1 이상이어야 합니다")
        @DefaultValue("7")
        int retentionDays,

        @Min(value = 1, message = "cleanupBatchSize 는 1 이상이어야 합니다")
        @DefaultValue("1000")
        int cleanupBatchSize,

        @Min(value = 0, message = "cleanupBatchSleepMs 는 0 이상이어야 합니다")
        @DefaultValue("50")
        long cleanupBatchSleepMs
) {
}
