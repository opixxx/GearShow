package com.gearshow.backend.platform.outbox.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Transactional Outbox 메시지 도메인 객체.
 *
 * <p>비즈니스 트랜잭션 안에서 함께 저장되며, 별도 Relay 스케줄러가
 * 이를 폴링하여 Kafka 로 발행한다. "DB 커밋 성공 = Kafka 발행 보장" 을 구현한다.</p>
 *
 * <p>페이로드는 이미 JSON 으로 직렬화된 문자열이며, Relay 는 이를 그대로
 * 바이트로 변환하여 Kafka 로 전달한다. 따라서 도메인 레이어는 Jackson 등
 * 직렬화 라이브러리 의존성을 갖지 않는다.</p>
 */
@Getter
public class OutboxMessage {

    private final Long id;
    private final String aggregateType;
    private final Long aggregateId;
    private final String eventType;
    private final String topic;
    private final String partitionKey;
    private final String messageId;
    private final String payload;
    private final boolean published;
    private final Instant createdAt;
    private final Instant publishedAt;

    @Builder
    private OutboxMessage(Long id, String aggregateType, Long aggregateId, String eventType,
                          String topic, String partitionKey, String messageId, String payload,
                          boolean published, Instant createdAt, Instant publishedAt) {
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
     * 새 Outbox 메시지를 생성한다. 신규 메시지는 발행되지 않은 상태로 시작한다.
     */
    public static OutboxMessage create(String aggregateType,
                                       Long aggregateId,
                                       String eventType,
                                       String topic,
                                       String partitionKey,
                                       String messageId,
                                       String payload) {
        validateRequired(aggregateType, aggregateId, eventType, topic, messageId, payload);
        return OutboxMessage.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .topic(topic)
                .partitionKey(partitionKey)
                .messageId(messageId)
                .payload(payload)
                .published(false)
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Kafka 발행이 성공한 뒤 상태를 published 로 전환한다.
     * 불변 객체이므로 새 인스턴스를 반환한다.
     */
    public OutboxMessage markPublished() {
        return OutboxMessage.builder()
                .id(this.id)
                .aggregateType(this.aggregateType)
                .aggregateId(this.aggregateId)
                .eventType(this.eventType)
                .topic(this.topic)
                .partitionKey(this.partitionKey)
                .messageId(this.messageId)
                .payload(this.payload)
                .published(true)
                .createdAt(this.createdAt)
                .publishedAt(Instant.now())
                .build();
    }

    private static void validateRequired(String aggregateType, Long aggregateId,
                                         String eventType, String topic,
                                         String messageId, String payload) {
        if (isBlank(aggregateType)) {
            throw new IllegalArgumentException("aggregateType 은 필수입니다");
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("aggregateId 는 필수입니다");
        }
        if (isBlank(eventType)) {
            throw new IllegalArgumentException("eventType 은 필수입니다");
        }
        if (isBlank(topic)) {
            throw new IllegalArgumentException("topic 은 필수입니다");
        }
        if (isBlank(messageId)) {
            throw new IllegalArgumentException("messageId 는 필수입니다");
        }
        if (isBlank(payload)) {
            throw new IllegalArgumentException("payload 는 필수입니다");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
