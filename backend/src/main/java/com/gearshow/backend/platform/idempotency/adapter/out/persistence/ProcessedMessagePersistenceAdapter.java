package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import com.gearshow.backend.platform.idempotency.application.port.out.ProcessedMessagePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 처리된 메시지 이력 Persistence Adapter.
 */
@Repository
@RequiredArgsConstructor
public class ProcessedMessagePersistenceAdapter implements ProcessedMessagePort {

    private final ProcessedMessageJpaRepository repository;

    @Override
    @Transactional
    public boolean saveIfAbsent(String messageId, String domain) {
        int inserted = repository.insertIfAbsent(messageId, domain, Instant.now());
        return inserted == 1;
    }

    @Override
    @Transactional
    public void release(String messageId, String domain) {
        repository.deleteByMessageIdAndDomain(messageId, domain);
    }

    @Override
    @Transactional
    public int deleteBatchOlderThan(Instant threshold, int batchSize) {
        return repository.deleteBatchOlderThan(threshold, batchSize);
    }
}
