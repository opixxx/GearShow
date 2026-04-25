package com.gearshow.backend.showcase.application.dto;

import java.time.Instant;

/**
 * 3D 모델 생성 워크플로우의 읽기 전용 스냅샷.
 *
 * <p>JPA 엔티티를 직접 노출하지 않기 위한 application 계층 값 객체. Worker·Poller·Reconcile 가
 * 현재 상태를 파악하거나 응답에 노출할 때 사용한다. 상태 전이는 반드시 포트의 조건부 UPDATE
 * 메서드를 통하므로 이 객체 자체는 setter 없이 불변이다.</p>
 *
 * <p>{@code failureCode}/{@code failureMessage} 는 워크플로우가 FAILED 로 종결된 경우에만 채워진다.
 * 응답 DTO 가 사용자에게 친화적인 메시지를 노출하고, 운영 디버깅용 코드는 로그에서 활용한다.</p>
 *
 * <p><b>분할 트리거</b> (P1-G aftermath 리뷰): 현재 11필드. 12필드 이상으로 확장될 시 (예: started_at,
 * finished_at, retry_count 추가) 의도별로 분할한다 — {@code WorkflowTransitionView} (id, currentStep,
 * tripoTaskId, tripoSucceededAt, heartbeatAt: Worker/Poller 상태 전이 판정용) +
 * {@code WorkflowDisplayView} (id, startedAt, finishedAt, failureCode, failureMessage: 사용자 응답용).</p>
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
        Instant heartbeatAt,
        String failureCode,
        String failureMessage
) {
}
