package com.gearshow.backend.showcase.application.dto;

import java.time.Instant;

/**
 * 3D 모델 생성 워크플로우의 읽기 전용 스냅샷.
 *
 * <p>JPA 엔티티를 직접 노출하지 않기 위한 application 계층 값 객체. Worker·Poller 가 현재
 * 상태를 파악하거나 로그를 남길 때 사용한다. 상태 전이는 반드시 포트의 조건부 UPDATE 메서드를
 * 통하므로 이 객체 자체는 setter 없이 불변이다.</p>
 */
public record WorkflowSnapshot(
        Long id,
        Long showcaseId,
        String idempotencyKey,
        int attemptNo,
        WorkflowStep currentStep,
        String tripoTaskId,
        Instant tripoSucceededAt,
        Instant startedAt,
        Instant heartbeatAt
) {
}
