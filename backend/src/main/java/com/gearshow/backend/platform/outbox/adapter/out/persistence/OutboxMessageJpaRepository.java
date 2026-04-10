package com.gearshow.backend.platform.outbox.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 메시지 JPA 저장소.
 */
public interface OutboxMessageJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    /**
     * 미발행 메시지를 생성 시각 오름차순으로 조회한다.
     * Pageable 로 LIMIT 을 전달하여 배치 크기를 제한한다.
     */
    @Query("SELECT o FROM OutboxMessageJpaEntity o" +
            " WHERE o.published = false" +
            " ORDER BY o.createdAt ASC, o.id ASC")
    List<OutboxMessageJpaEntity> findPendingBatch(Pageable pageable);

    /**
     * 지정 시각 이전에 발행 완료된 레코드를 PK 순서로 배치 삭제한다.
     *
     * <p>인덱스 락 경합을 최소화하기 위해 LIMIT 으로 배치 크기를 제한한다.
     * 정리 스케줄러가 이 메서드를 반복 호출하여 전체 이력을 정리한다.</p>
     *
     * @return 실제 삭제된 행 수
     */
    @Modifying
    @Query(value = """
            DELETE FROM outbox_message
            WHERE published = TRUE
              AND published_at < :threshold
            ORDER BY outbox_message_id
            LIMIT :batchSize
            """, nativeQuery = true)
    int deletePublishedBatchOlderThan(@Param("threshold") Instant threshold,
                                      @Param("batchSize") int batchSize);

    /**
     * 단일 UPDATE 로 published 플래그와 publishedAt 을 갱신한다.
     *
     * <p>dirty checking 방식(findById → 엔티티 수정) 은 SELECT+UPDATE 두 쿼리가 발생하는데,
     * Relay 는 1초 주기 × 배치 크기만큼 호출되는 hot path 이므로 단일 UPDATE 로 최적화한다.
     * WHERE 절에 {@code published = FALSE} 를 포함시켜 동시에 두 Relay 인스턴스가
     * 같은 메시지를 마킹하려 할 때 0 row affected 가 발생하도록 DB 레벨 보호를 둔다.</p>
     *
     * @return 실제 갱신된 행 수 (0 이면 이미 다른 프로세스가 마킹 완료)
     */
    @Modifying
    @Query("UPDATE OutboxMessageJpaEntity o" +
            "   SET o.published = true, o.publishedAt = :publishedAt" +
            " WHERE o.id = :id AND o.published = false")
    int markPublishedById(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);
}
