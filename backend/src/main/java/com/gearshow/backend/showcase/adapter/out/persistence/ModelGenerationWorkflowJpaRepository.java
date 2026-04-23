package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
