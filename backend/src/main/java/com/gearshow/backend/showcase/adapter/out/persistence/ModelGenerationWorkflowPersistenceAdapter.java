package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.exception.ConcurrentModelRetryException;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

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
 */
@Repository
@RequiredArgsConstructor
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
}
