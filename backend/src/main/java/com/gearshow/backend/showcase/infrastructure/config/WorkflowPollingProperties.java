package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 워크플로우 폴링 인프라 설정 (P1-E DelayedQueue 경로 전용).
 *
 * <p>P1-G aftermath 에서 {@code TripoPollingProperties} 를 분해하면서, "Tripo 호출 빈도" 가 아니라
 * "워크플로우 폴링 큐 동작" 을 다룬다는 의도를 이름에 반영한다. Tripo HTTP 클라이언트의 세마포어
 * 타임아웃은 별도의 {@link TripoApiProperties} 로 이전했다.</p>
 *
 * @param delaySeconds     Worker/Poller 가 DelayedQueue 에 re-offer 할 기본 지연(초). 설계 §6.2 30초.
 * @param schedulerEnabled {@link com.gearshow.backend.showcase.adapter.in.scheduler.WorkflowPollingScheduler}
 *                         구동 여부. 기본 활성.
 */
@Validated
@ConfigurationProperties(prefix = "app.workflow-polling")
public record WorkflowPollingProperties(
        @Min(value = 1, message = "delaySeconds 는 1 이상이어야 합니다")
        @DefaultValue("30")
        int delaySeconds,

        @DefaultValue("true")
        boolean schedulerEnabled
) {
}
