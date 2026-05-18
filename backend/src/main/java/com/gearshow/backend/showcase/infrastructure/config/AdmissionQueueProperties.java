package com.gearshow.backend.showcase.infrastructure.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 3D 생성 입장 큐(admission queue) 설정 (ADR-025).
 *
 * <p>{@code @ConfigurationPropertiesScan} 으로 자동 등록된다.</p>
 *
 * <p>{@code app.model-generation.admission.drain-interval-ms} 는
 * {@code @Scheduled(fixedDelayString)} placeholder 로만 소비되므로(애노테이션 제약) 본 record
 * 필드로 두지 않는다 — yml 키는 ADR-025 대로 유지된다.</p>
 *
 * @param maxConcurrent 동시 생성 작업 cap. 활성(PREPARING+GENERATING) 수가 이 값 이상이면
 *                      신규 요청을 {@code tripo:queue} 에 park 한다. soft cap (ADR-025 §4).
 * @param enabled       입장 게이트 활성 여부. {@code false} 면 게이트가 항상 통과 = 기능 도입 전
 *                      동작(롤백 스위치, ADR-025 §2-8 — drainer 빈 미등록 + Reconcile 재park 비활성).
 */
@Validated
@ConfigurationProperties(prefix = "app.model-generation.admission")
public record AdmissionQueueProperties(
        @Min(value = 1, message = "maxConcurrent 는 1 이상이어야 합니다")
        @DefaultValue("10")
        int maxConcurrent,

        @DefaultValue("true")
        boolean enabled
) {
}
