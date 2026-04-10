package com.gearshow.backend.platform.outbox.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outbox 메시지 JPA 엔티티.
 *
 * <p>{@code (published, created_at)} 복합 인덱스를 둬서 Relay 스케줄러의
 * "미발행 메시지 조회" 쿼리를 최적화한다. {@code message_id} 는 UNIQUE 로
 * 발행 측에서 같은 메시지를 두 번 기록하는 것을 막는다.</p>
 */
@Entity
@Table(
        name = "outbox_message",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_outbox_message_message_id",
                        columnNames = {"message_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_outbox_message_published_created",
                        columnList = "published, created_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_message_id")
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "partition_key", length = 200)
    private String partitionKey;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    /**
     * 이벤트 페이로드 JSON.
     * {@code @Lob} 을 쓰면 MySQL 에서 LONGTEXT(4GB) 로 매핑되어 row 외부 페이지에 저장되고
     * SELECT/정렬 시 부하가 커진다. 현재 요구사항은 수 KB 이내이므로 TEXT(64KB) 로 충분하다.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Builder
    private OutboxMessageJpaEntity(Long id, String aggregateType, Long aggregateId,
                                   String eventType, String topic, String partitionKey,
                                   String messageId, String payload, boolean published,
                                   Instant createdAt, Instant publishedAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.partitionKey = partitionKey;
        this.messageId = messageId;
        this.payload = payload;
        this.published = published;
        this.createdAt = createdAt;
        this.publishedAt = publishedAt;
    }

    /**
     * 발행 완료 상태로 업데이트한다 (inline update, JPA dirty checking).
     */
    public void markPublished(Instant publishedAt) {
        this.published = true;
        this.publishedAt = publishedAt;
    }
}
