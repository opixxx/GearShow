package com.gearshow.backend.platform.outbox.adapter.out.persistence;

import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 메시지 Persistence Adapter.
 *
 * <p>{@link #markPublished(Long)} 는 dirty checking 을 사용하지 않고
 * 전용 UPDATE 쿼리 한 번으로 published 플래그와 publishedAt 을 갱신한다.
 * Relay 가 1초 주기 × 배치 100건으로 hot-path 이므로 SELECT+merge 2쿼리를 피한다.
 * 또한 WHERE 조건에 {@code published = false} 를 포함시켜 동시성 환경에서 중복 마킹을 DB 레벨에서 방어한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OutboxMessagePersistenceAdapter implements OutboxMessagePort {

    private final OutboxMessageJpaRepository repository;

    @Override
    @Transactional
    public OutboxMessage save(OutboxMessage message) {
        OutboxMessageJpaEntity entity = OutboxMessageMapper.toEntity(message);
        OutboxMessageJpaEntity saved = repository.save(entity);
        return OutboxMessageMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxMessage> findPendingBatch(int limit) {
        return repository.findPendingBatch(PageRequest.of(0, limit)).stream()
                .map(OutboxMessageMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(Long id) {
        repository.markPublishedById(id, Instant.now());
    }

    @Override
    @Transactional
    public int deletePublishedBatchOlderThan(Instant threshold, int batchSize) {
        return repository.deletePublishedBatchOlderThan(threshold, batchSize);
    }
}
