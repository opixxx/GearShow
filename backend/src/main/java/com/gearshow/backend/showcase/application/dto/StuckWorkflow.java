package com.gearshow.backend.showcase.application.dto;

import java.time.Instant;

/**
 * Reconcile 배치용 stuck 워크플로우 스냅샷 (설계 §8.4).
 *
 * <p>{@link WorkflowSnapshot} 보다 Reconcile 복구에 필요한 최소 필드만 담는 읽기 전용 값 객체.
 * Reconcile 서비스는 이 값만으로 복구 액션 분기(PREPARING/GENERATING-Tripo/GENERATING-S3)를
 * 결정하고, 락 획득 후 필요한 경우 전체 스냅샷/트립오 pending task 를 재조회한다.</p>
 */
public record StuckWorkflow(
        Long id,
        Long showcaseId,
        WorkflowStep currentStep,
        String tripoTaskId,
        Instant tripoSucceededAt,
        Instant heartbeatAt,
        Instant lastPolledAt,
        Instant startedAt,
        Instant createdAt
) {
}
