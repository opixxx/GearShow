package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tripo HTTP API 호출 관련 설정.
 *
 * <p>P1-G aftermath 에서 {@code TripoPollingProperties} 의 {@code semaphoreAcquireTimeoutMs} 를
 * 분리해 가져왔다. Tripo 자체 통신 동작 (세마포어, 추후 Circuit Breaker 추가 시 별도 옵션) 만
 * 다룬다.</p>
 *
 * @param semaphoreAcquireTimeoutMs Tripo semaphore permit 획득 대기 상한(ms).
 *                                  초과 시 {@code TripoSemaphoreTimeoutException}.
 */
@Validated
@ConfigurationProperties(prefix = "app.tripo-api")
public record TripoApiProperties(
        @Min(value = 100, message = "semaphoreAcquireTimeoutMs 는 100 이상이어야 합니다")
        @DefaultValue("2000")
        long semaphoreAcquireTimeoutMs
) {
}
