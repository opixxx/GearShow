package com.gearshow.backend.platform.outbox.application.port.out;

import com.gearshow.backend.platform.outbox.domain.OutboxMessage;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 메시지 저장소 Outbound Port.
 */
public interface OutboxMessagePort {

    /**
     * 새 Outbox 메시지를 저장한다.
     * 일반적으로 비즈니스 트랜잭션 안에서 함께 호출되어 원자성을 보장한다.
     */
    OutboxMessage save(OutboxMessage message);

    /**
     * 아직 발행되지 않은(published = false) 메시지를
     * 생성 시각 오름차순으로 최대 {@code limit} 건 조회한다.
     */
    List<OutboxMessage> findPendingBatch(int limit);

    /**
     * 메시지를 발행 완료 상태로 마킹한다.
     * 호출 시점이 {@code publishedAt} 으로 기록된다.
     */
    void markPublished(Long id);

    /**
     * 지정 시각 이전에 발행 완료된 레코드를 PK 순서로 배치 삭제한다.
     * 운영상 감사 로그 목적으로 유지하다가 주기적으로 정리한다.
     *
     * @return 실제 삭제된 행 수
     */
    int deletePublishedBatchOlderThan(Instant threshold, int batchSize);
}
