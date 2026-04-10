package com.gearshow.backend.platform.outbox.adapter.out.persistence;

import com.gearshow.backend.platform.outbox.domain.OutboxMessage;

/**
 * 도메인 {@link OutboxMessage} <-> JPA 엔티티 변환.
 */
final class OutboxMessageMapper {

    private OutboxMessageMapper() {
    }

    static OutboxMessageJpaEntity toEntity(OutboxMessage domain) {
        return OutboxMessageJpaEntity.builder()
                .id(domain.getId())
                .aggregateType(domain.getAggregateType())
                .aggregateId(domain.getAggregateId())
                .eventType(domain.getEventType())
                .topic(domain.getTopic())
                .partitionKey(domain.getPartitionKey())
                .messageId(domain.getMessageId())
                .payload(domain.getPayload())
                .published(domain.isPublished())
                .createdAt(domain.getCreatedAt())
                .publishedAt(domain.getPublishedAt())
                .build();
    }

    static OutboxMessage toDomain(OutboxMessageJpaEntity entity) {
        return OutboxMessage.builder()
                .id(entity.getId())
                .aggregateType(entity.getAggregateType())
                .aggregateId(entity.getAggregateId())
                .eventType(entity.getEventType())
                .topic(entity.getTopic())
                .partitionKey(entity.getPartitionKey())
                .messageId(entity.getMessageId())
                .payload(entity.getPayload())
                .published(entity.isPublished())
                .createdAt(entity.getCreatedAt())
                .publishedAt(entity.getPublishedAt())
                .build();
    }
}
