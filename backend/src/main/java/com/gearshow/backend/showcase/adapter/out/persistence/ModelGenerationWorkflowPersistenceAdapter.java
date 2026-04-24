package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.dto.StuckWorkflow;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.exception.ConcurrentModelRetryException;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@link ModelGenerationWorkflowPort} 의 JPA 기반 어댑터.
 *
 * <p>상위 서비스가 열어둔 트랜잭션에 참여(REQUIRED)하여, 쇼케이스 등록 / 3D 모델 요청과
 * 같은 원자 경계 안에서 워크플로우 레코드를 INSERT 한다. 재시도는 항상 새 행을 추가해
 * 이력을 보존한다 (ADR-010).</p>
 *
 * <p>{@code uk_mgw_showcase_attempt (showcase_id, attempt_no)} UNIQUE 제약은
 * {@link #nextAttemptNo(Long)} → {@link #saveRequested(Long, String, int)} 사이에 발생할 수 있는
 * race 를 DB 레벨에서 차단한다. 충돌은 비즈니스 예외 {@link ConcurrentModelRetryException}(409) 로
 * 전환해 호출자가 의미 있는 응답을 내려보낼 수 있게 한다.</p>
 *
 * <p><b>트랜잭션 전략</b>: {@code Showcase3dModelPersistenceAdapter} 와 동일하게 클래스 레벨
 * {@code @Transactional} 을 두어 상위 TX 에 참여(REQUIRED)하거나 없을 땐 메서드당 한 번만 연다.
 * Poller 경로처럼 상위 서비스에 TX 가 없는 경우라도 각 mark* 호출이 예측 가능한 범위에서 트랜잭션
 * 경계를 갖게 한다.</p>
 */
@Repository
@RequiredArgsConstructor
@Transactional
public class ModelGenerationWorkflowPersistenceAdapter implements ModelGenerationWorkflowPort {

    private final ModelGenerationWorkflowJpaRepository workflowJpaRepository;

    @Override
    public long saveRequested(Long showcaseId, String idempotencyKey, int attemptNo) {
        try {
            ModelGenerationWorkflowJpaEntity saved = workflowJpaRepository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(showcaseId, idempotencyKey, attemptNo));
            return saved.getId();
        } catch (DataIntegrityViolationException e) {
            // (showcase_id, attempt_no) UNIQUE 충돌 — 동시 재시도로 인한 race
            throw new ConcurrentModelRetryException();
        }
    }

    @Override
    public int nextAttemptNo(Long showcaseId) {
        return workflowJpaRepository
                .findTopByShowcaseIdOrderByAttemptNoDesc(showcaseId)
                .map(entity -> entity.getAttemptNo() + 1)
                .orElse(1);
    }

    @Override
    public Optional<WorkflowSnapshot> findSnapshot(Long workflowId) {
        return workflowJpaRepository.findById(workflowId)
                .map(this::toSnapshot);
    }

    @Override
    public int updateStepIfCurrent(Long workflowId, WorkflowStep expected, WorkflowStep next) {
        return workflowJpaRepository.updateStepIfCurrent(
                workflowId, expected, next, Instant.now());
    }

    @Override
    public int markFailed(Long workflowId, WorkflowFailureCode code, String message, String source) {
        return workflowJpaRepository.markFailed(
                workflowId, code.name(), message, source, Instant.now());
    }

    @Override
    public int markGenerating(Long workflowId, String tripoTaskId) {
        return workflowJpaRepository.markGenerating(workflowId, tripoTaskId, Instant.now());
    }

    @Override
    public int markPolled(Long workflowId) {
        return workflowJpaRepository.markPolled(workflowId, Instant.now());
    }

    @Override
    public int markTripoSucceeded(Long workflowId) {
        return workflowJpaRepository.markTripoSucceeded(workflowId, Instant.now());
    }

    @Override
    public int markCompleted(Long workflowId) {
        return workflowJpaRepository.markCompleted(workflowId, Instant.now());
    }

    @Override
    public Optional<WorkflowSnapshot> findLatestSnapshotByShowcaseId(Long showcaseId) {
        return workflowJpaRepository
                .findTopByShowcaseIdOrderByAttemptNoDesc(showcaseId)
                .map(this::toSnapshot);
    }

    @Override
    public List<StuckWorkflow> findStuckPreparing(Instant heartbeatBefore, int limit) {
        return workflowJpaRepository
                .findStuckPreparing(heartbeatBefore, PageRequest.of(0, limit))
                .stream()
                .map(this::toStuckWorkflow)
                .toList();
    }

    @Override
    public List<StuckWorkflow> findStuckGeneratingTripo(Instant lastPolledBefore, int limit) {
        return workflowJpaRepository
                .findStuckGeneratingTripo(lastPolledBefore, PageRequest.of(0, limit))
                .stream()
                .map(this::toStuckWorkflow)
                .toList();
    }

    @Override
    public List<StuckWorkflow> findStuckGeneratingS3(Instant heartbeatBefore, int limit) {
        return workflowJpaRepository
                .findStuckGeneratingS3(heartbeatBefore, PageRequest.of(0, limit))
                .stream()
                .map(this::toStuckWorkflow)
                .toList();
    }

    @Override
    public List<StuckWorkflow> findStuckRequested(Instant createdBefore, int limit) {
        return workflowJpaRepository
                .findStuckRequested(createdBefore, PageRequest.of(0, limit))
                .stream()
                .map(this::toStuckWorkflow)
                .toList();
    }

    private StuckWorkflow toStuckWorkflow(ModelGenerationWorkflowJpaEntity entity) {
        return new StuckWorkflow(
                entity.getId(),
                entity.getShowcaseId(),
                entity.getCurrentStep(),
                entity.getTripoTaskId(),
                entity.getTripoSucceededAt(),
                entity.getHeartbeatAt(),
                entity.getLastPolledAt(),
                entity.getCreatedAt()
        );
    }

    private WorkflowSnapshot toSnapshot(ModelGenerationWorkflowJpaEntity entity) {
        return new WorkflowSnapshot(
                entity.getId(),
                entity.getShowcaseId(),
                entity.getIdempotencyKey(),
                entity.getAttemptNo(),
                entity.getCurrentStep(),
                entity.getTripoTaskId(),
                entity.getTripoSucceededAt(),
                entity.getStartedAt(),
                entity.getHeartbeatAt()
        );
    }
}
