package com.gearshow.backend.showcase.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tripo {@code POST /task} 성공 직후 {@code task_id} 를 선저장하는 임시 테이블.
 *
 * <p>워커가 Tripo POST 성공 후 TX2 커밋 전에 크래시할 경우를 대비해, 응답받은 {@code task_id} 를
 * 즉시 이 테이블에 기록한다. Reconcile 이 PREPARING stuck 을 감지하고 복구할 때 이 테이블을
 * 조회하여 이미 발행된 Tripo task 를 재사용하므로 중복 과금을 방지한다.</p>
 *
 * <p>TX2 에서 {@code model_generation_workflow.tripo_task_id} 로 승격된 뒤엔 즉시 DELETE.</p>
 *
 * <p><b>설계 근거</b>: ADR-011 (다층 멱등성 ④ Tripo 계층).</p>
 *
 * <p><b>PK 설계</b>: {@code workflow_id} 를 PK 로 직접 사용 (surrogate 키 없음).
 * 값은 호출자(워커) 가 전달하므로 자동 생성 전략을 쓰지 않는다.</p>
 */
@Entity
@Table(name = "tripo_pending_task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripoPendingTaskJpaEntity {

    @Id
    @Column(name = "workflow_id")
    private Long workflowId;

    @Column(name = "task_id", nullable = false, length = 64)
    private String taskId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private TripoPendingTaskJpaEntity(Long workflowId, String taskId, Instant createdAt) {
        this.workflowId = workflowId;
        this.taskId = taskId;
        this.createdAt = createdAt;
    }

    /**
     * Tripo POST 성공 직후 선저장을 생성한다.
     */
    public static TripoPendingTaskJpaEntity of(Long workflowId, String taskId) {
        return new TripoPendingTaskJpaEntity(workflowId, taskId, Instant.now());
    }
}
