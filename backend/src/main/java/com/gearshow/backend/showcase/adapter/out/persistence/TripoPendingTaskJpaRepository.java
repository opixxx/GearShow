package com.gearshow.backend.showcase.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripoPendingTaskJpaRepository extends JpaRepository<TripoPendingTaskJpaEntity, Long> {

    /**
     * {@code workflow_id} 로 pending 레코드를 삭제한다. 존재하지 않으면 affected=0 을 반환하며
     * 예외를 던지지 않아 멱등적이다. PK 기반 단일 {@code DELETE} 라 {@code existsById}
     * 조회 왕복을 피할 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TripoPendingTaskJpaEntity t WHERE t.workflowId = :workflowId")
    int deleteByWorkflowIdIfExists(@Param("workflowId") Long workflowId);
}
