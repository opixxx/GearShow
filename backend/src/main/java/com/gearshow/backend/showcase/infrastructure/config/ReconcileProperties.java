package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Max;
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
 * @param requestedStuckSeconds         REQUESTED stuck 임계. <b>이 노브는 두 역할을
 *                                      멀티플렉싱한다 (ADR-025 NN7)</b>: (i) Outbox-relay-stuck
 *                                      탐지 지연 ↔ (ii) admission in-flight 오탐 억제(재발행분이
 *                                      Kafka 왕복+첫 retry backoff 30s 동안 REQUESTED 인데
 *                                      너무 짧으면 Reconcile 이 곧장 재park → 재시도 체인 증폭).
 *                                      현실 in-flight+첫 backoff 위로 둔다(기본 300). 상향 시
 *                                      Outbox-stuck 탐지 지연도 함께 커진다 — 정공법은 옵션 (b)
 *                                      (DB dispatched_at/ADMITTED, ADR-025 §4 D1, 트리거=
 *                                      멀티인스턴스∨지속부하∨Outbox-stuck 지연 중 먼저).
 * @param workflowMaxLifetimeMinutes    GENERATING-Tripo 단계 lifetime cap (분). 정상 ~2분 × 2.5배 마진.
 *                                      초과 시 {@code TRIPO_TIMEOUT_GENERATING} 으로 강제 손절해
 *                                      Tripo 가 영원히 running 만 응답하는 시나리오에서 무한 redrive 를
 *                                      차단한다. 운영 1개월 관측 후 재조정.
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
        @DefaultValue("300")
        long requestedStuckSeconds,

        @Min(value = 2, message = "workflowMaxLifetimeMinutes 는 2 이상이어야 합니다")
        @Max(value = 60, message = "workflowMaxLifetimeMinutes 는 60 이하여야 합니다")
        @DefaultValue("5")
        long workflowMaxLifetimeMinutes
) {
}
