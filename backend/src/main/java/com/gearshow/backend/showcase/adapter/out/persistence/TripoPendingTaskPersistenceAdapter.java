package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * {@link TripoPendingTaskPort} 의 JPA 기반 어댑터.
 *
 * <p>{@code tripo_pending_task} 테이블은 {@code workflow_id} 를 PK 로 쓰는 단순 키-값 저장소.
 * 멱등적 DELETE 를 위해 {@code deleteById} 가 존재하지 않는 키에 대해 조용히 통과하도록
 * {@link org.springframework.data.repository.CrudRepository#existsById} 로 사전 확인한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class TripoPendingTaskPersistenceAdapter implements TripoPendingTaskPort {

    private final TripoPendingTaskJpaRepository jpaRepository;

    @Override
    public void preserve(Long workflowId, String tripoTaskId) {
        jpaRepository.saveAndFlush(
                TripoPendingTaskJpaEntity.preservingTaskId(workflowId, tripoTaskId));
    }

    @Override
    public void deleteByWorkflowId(Long workflowId) {
        // 단일 DELETE 쿼리로 멱등 처리 — 존재하지 않아도 affected=0 을 반환할 뿐 예외 없음.
        jpaRepository.deleteByWorkflowIdIfExists(workflowId);
    }
}
