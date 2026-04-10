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
 */
@Repository
@RequiredArgsConstructor
public class OutboxMessagePersistenceAdapter implements OutboxMessagePort {

    private final OutboxMessageJpaRepository repository;

    @Override
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
        repository.findById(id).ifPresent(entity -> entity.markPublished(Instant.now()));
    }

    @Override
    @Transactional
    public int deletePublishedBatchOlderThan(Instant threshold, int batchSize) {
        return repository.deletePublishedBatchOlderThan(threshold, batchSize);
    }
}
