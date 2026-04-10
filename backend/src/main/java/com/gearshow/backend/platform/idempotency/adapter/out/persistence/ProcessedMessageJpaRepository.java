package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

/**
 * 처리된 메시지 이력 JPA 저장소.
 */
public interface ProcessedMessageJpaRepository extends JpaRepository<ProcessedMessageJpaEntity, Long> {

    /**
     * 처리 이력을 INSERT IGNORE로 저장한다.
     *
     * <p>UNIQUE 제약 위반 시 예외가 발생하지 않고 0행이 반환된다.
     * 이를 통해 try-catch 없이도 멱등성을 보장할 수 있고,
     * Spring 트랜잭션의 rollback-only 플래그 오염을 회피한다.</p>
     *
     * @return 신규 저장이면 1, 이미 존재하면 0
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO processed_message (message_id, domain, processed_at)
            VALUES (:messageId, :domain, :processedAt)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") String messageId,
                       @Param("domain") String domain,
                       @Param("processedAt") Instant processedAt);

    /**
     * 지정 시각 이전 이력을 PK 순서로 배치 단위 삭제한다.
     *
     * <p>한 번에 모든 행을 삭제하면 인덱스 락이 장시간 보유되어
     * 동시에 들어오는 INSERT(메시지 처리)와 락 경합이 발생한다.
     * 배치 삭제로 트랜잭션을 짧게 유지하여 락 경합을 최소화한다.</p>
     *
     * @return 실제 삭제된 행 수ㄴ
     */
    @Modifying
    @Query(value = """
            DELETE FROM processed_message
            WHERE processed_at < :threshold
            ORDER BY processed_message_id
            LIMIT :batchSize
            """, nativeQuery = true)
    int deleteBatchOlderThan(@Param("threshold") Instant threshold,
                             @Param("batchSize") int batchSize);
}
