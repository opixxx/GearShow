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

    /**
     * INSERT IGNORE 네이티브 쿼리 자체가 원자적이라 짧은 자체 TX 만으로 충분하다.
     * 호출자 (ModelGenerationWorker) 가 비-트랜잭션 컨텍스트라 propagation 명시는 불필요.
     * 미래에 트랜잭션 컨텍스트에서 호출되면 호출자 TX 합류 → 호출자 롤백 시 이력도 롤백.
     * "완료 시점 INSERT" 시멘틱 보호가 필요하면 그때 REQUIRES_NEW 로 전환.
     */
    @Override
    @Transactional
    public boolean saveIfAbsent(String messageId, String domain) {
        int inserted = repository.insertIfAbsent(messageId, domain, Instant.now());
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByKey(String messageId, String domain) {
        return repository.existsByMessageIdAndDomain(messageId, domain);
    }

    @Override
    @Transactional
    public int deleteBatchOlderThan(Instant threshold, int batchSize) {
        return repository.deleteBatchOlderThan(threshold, batchSize);
    }
}
