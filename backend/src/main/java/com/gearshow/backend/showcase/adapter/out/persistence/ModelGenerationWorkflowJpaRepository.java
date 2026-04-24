package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ModelGenerationWorkflowJpaRepository
        extends JpaRepository<ModelGenerationWorkflowJpaEntity, Long> {

    Optional<ModelGenerationWorkflowJpaEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 특정 쇼케이스의 마지막 {@code attempt_no} 워크플로우를 조회한다.
     * {@code idx_mgw_showcase_attempt} 인덱스를 이용해 재시도 순번을 계산할 때 쓴다.
     */
    Optional<ModelGenerationWorkflowJpaEntity>
            findTopByShowcaseIdOrderByAttemptNoDesc(Long showcaseId);

    /**
     * 조건부 상태 전이 (ADR-012). WHERE current_step = :expected 조건을 걸어 동시 Worker
     * 중복 처리를 DB 레벨에서 차단한다. REQUESTED → PREPARING 전이일 때만 {@code started_at}
     * 도 함께 초기화한다 (최초 시작 시각 고정).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ModelGenerationWorkflowJpaEntity w
               SET w.currentStep = :next,
                   w.heartbeatAt = :now,
                   w.updatedAt = :now,
                   w.startedAt = CASE
                       WHEN :expected = com.gearshow.backend.showcase.application.dto.WorkflowStep.REQUESTED
                            AND :next = com.gearshow.backend.showcase.application.dto.WorkflowStep.PREPARING
                       THEN :now
                       ELSE w.startedAt
                   END
             WHERE w.id = :id
               AND w.currentStep = :expected
            """)
    int updateStepIfCurrent(@Param("id") Long id,
                            @Param("expected") WorkflowStep expected,
                            @Param("next") WorkflowStep next,
                            @Param("now") Instant now);

    /**
     * 워크플로우를 FAILED 로 마킹한다. 이미 종료된 상태(COMPLETED/FAILED)는 덮어쓰지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ModelGenerationWorkflowJpaEntity w
               SET w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.FAILED,
                   w.failureCode = :code,
                   w.failureMessage = :message,
                   w.failureSource = :source,
                   w.finishedAt = :now,
                   w.updatedAt = :now
             WHERE w.id = :id
               AND w.currentStep <> com.gearshow.backend.showcase.application.dto.WorkflowStep.COMPLETED
               AND w.currentStep <> com.gearshow.backend.showcase.application.dto.WorkflowStep.FAILED
            """)
    int markFailed(@Param("id") Long id,
                   @Param("code") String code,
                   @Param("message") String message,
                   @Param("source") String source,
                   @Param("now") Instant now);

    /**
     * PREPARING → GENERATING 전이 (TX2). {@code tripo_task_id} 를 저장하며 heartbeat 갱신.
     * WHERE current_step=PREPARING 조건으로 race 차단.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ModelGenerationWorkflowJpaEntity w
               SET w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.GENERATING,
                   w.tripoTaskId = :taskId,
                   w.heartbeatAt = :now,
                   w.updatedAt = :now
             WHERE w.id = :id
               AND w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.PREPARING
            """)
    int markGenerating(@Param("id") Long id,
                       @Param("taskId") String taskId,
                       @Param("now") Instant now);

    /**
     * Poller 가 Tripo 를 조회했음을 기록한다. GENERATING + 아직 SUCCESS 인지 안 된 워크플로우만 대상.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ModelGenerationWorkflowJpaEntity w
               SET w.lastPolledAt = :now,
                   w.heartbeatAt = :now,
                   w.updatedAt = :now
             WHERE w.id = :id
               AND w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.GENERATING
               AND w.tripoSucceededAt IS NULL
            """)
    int markPolled(@Param("id") Long id, @Param("now") Instant now);

    /**
     * GENERATING 내부에서 Tripo SUCCESS 인지 시점을 기록한다. {@code tripo_succeeded_at} 만
     * 채우고 {@code current_step} 은 GENERATING 유지 (S3 미러링 서브-단계로 진입).
     * WHERE 에 {@code tripo_succeeded_at IS NULL} 을 포함해 중복 이벤트 발행을 구조적으로 차단.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ModelGenerationWorkflowJpaEntity w
               SET w.tripoSucceededAt = :now,
                   w.lastPolledAt = :now,
                   w.heartbeatAt = :now,
                   w.updatedAt = :now
             WHERE w.id = :id
               AND w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.GENERATING
               AND w.tripoSucceededAt IS NULL
            """)
    int markTripoSucceeded(@Param("id") Long id, @Param("now") Instant now);

    /**
     * TX_final: {@code GENERATING → COMPLETED} 전이 (설계 §5, §7 [8]). WHERE 에
     * {@code current_step=GENERATING AND tripo_succeeded_at IS NOT NULL} 을 강제해 Poller SUCCESS
     * 인지 전 재진입과 이미 COMPLETED 된 워크플로우 덮어쓰기를 차단한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ModelGenerationWorkflowJpaEntity w
               SET w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.COMPLETED,
                   w.finishedAt = :now,
                   w.heartbeatAt = :now,
                   w.updatedAt = :now
             WHERE w.id = :id
               AND w.currentStep = com.gearshow.backend.showcase.application.dto.WorkflowStep.GENERATING
               AND w.tripoSucceededAt IS NOT NULL
            """)
    int markCompleted(@Param("id") Long id, @Param("now") Instant now);
}
