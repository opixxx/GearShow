package com.gearshow.backend.platform.outbox.domain;

import com.gearshow.backend.platform.outbox.domain.exception.InvalidOutboxMessageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OutboxMessage 도메인 단위 테스트.
 *
 * <p>도메인 불변식과 팩토리/전이 메서드의 불변성을 검증한다.
 * Spring, JPA 의존 없이 순수 자바 + AssertJ 만 사용한다.</p>
 */
class OutboxMessageTest {

    private static final String AGGREGATE_TYPE = "SHOWCASE_3D_MODEL";
    private static final Long AGGREGATE_ID = 1L;
    private static final String EVENT_TYPE = "MODEL_GENERATION_REQUESTED";
    private static final String TOPIC = "showcase.model.generation.request";
    private static final String PARTITION_KEY = "100";
    private static final String MESSAGE_ID = "msg-uuid-1234";
    private static final String PAYLOAD = "{\"showcase3dModelId\":1,\"showcaseId\":100}";

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("필수 값이 모두 주어지면 published=false, createdAt 이 자동 세팅된 메시지를 생성한다")
        void create_withAllRequiredFields_returnsUnpublishedMessage() {
            // Given
            Instant before = Instant.now();

            // When
            OutboxMessage message = OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE,
                    TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD);

            // Then
            assertThat(message.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
            assertThat(message.getAggregateId()).isEqualTo(AGGREGATE_ID);
            assertThat(message.getEventType()).isEqualTo(EVENT_TYPE);
            assertThat(message.getTopic()).isEqualTo(TOPIC);
            assertThat(message.getPartitionKey()).isEqualTo(PARTITION_KEY);
            assertThat(message.getMessageId()).isEqualTo(MESSAGE_ID);
            assertThat(message.getPayload()).isEqualTo(PAYLOAD);
            assertThat(message.isPublished()).isFalse();
            assertThat(message.getPublishedAt()).isNull();
            // createdAt 은 호출 시점 이후로 세팅되어야 한다
            assertThat(message.getCreatedAt()).isNotNull();
            assertThat(message.getCreatedAt()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("partitionKey 는 null 이어도 생성에 성공한다 (파티션 할당을 Kafka 에 위임)")
        void create_withNullPartitionKey_succeeds() {
            // Given & When
            OutboxMessage message = OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE,
                    TOPIC, null, MESSAGE_ID, PAYLOAD);

            // Then
            assertThat(message.getPartitionKey()).isNull();
            assertThat(message.isPublished()).isFalse();
        }

        @Test
        @DisplayName("aggregateType 이 null 이면 InvalidOutboxMessageException 이 발생한다")
        void create_withNullAggregateType_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    null, AGGREGATE_ID, EVENT_TYPE, TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }

        @Test
        @DisplayName("aggregateType 이 공백이면 InvalidOutboxMessageException 이 발생한다")
        void create_withBlankAggregateType_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    "   ", AGGREGATE_ID, EVENT_TYPE, TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }

        @Test
        @DisplayName("aggregateId 가 null 이면 InvalidOutboxMessageException 이 발생한다")
        void create_withNullAggregateId_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    AGGREGATE_TYPE, null, EVENT_TYPE, TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }

        @Test
        @DisplayName("eventType 이 공백이면 InvalidOutboxMessageException 이 발생한다")
        void create_withBlankEventType_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, "", TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }

        @Test
        @DisplayName("topic 이 공백이면 InvalidOutboxMessageException 이 발생한다")
        void create_withBlankTopic_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, "", PARTITION_KEY, MESSAGE_ID, PAYLOAD))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }

        @Test
        @DisplayName("messageId 가 null 이면 InvalidOutboxMessageException 이 발생한다")
        void create_withNullMessageId_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, TOPIC, PARTITION_KEY, null, PAYLOAD))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }

        @Test
        @DisplayName("payload 가 공백이면 InvalidOutboxMessageException 이 발생한다")
        void create_withBlankPayload_throwsException() {
            // When & Then
            assertThatThrownBy(() -> OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, TOPIC, PARTITION_KEY, MESSAGE_ID, "  "))
                    .isInstanceOf(InvalidOutboxMessageException.class);        }
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("markPublished 는 새 인스턴스를 반환하고 published=true, publishedAt 이 설정된다")
        void markPublished_returnsNewInstanceWithPublishedFlag() {
            // Given
            OutboxMessage original = OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE,
                    TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD);
            Instant before = Instant.now();

            // When
            OutboxMessage published = original.markPublished();

            // Then
            assertThat(published.isPublished()).isTrue();
            assertThat(published.getPublishedAt()).isNotNull();
            assertThat(published.getPublishedAt()).isAfterOrEqualTo(before);

            // 주요 식별 필드는 그대로 유지되어야 한다
            assertThat(published.getAggregateType()).isEqualTo(original.getAggregateType());
            assertThat(published.getAggregateId()).isEqualTo(original.getAggregateId());
            assertThat(published.getEventType()).isEqualTo(original.getEventType());
            assertThat(published.getTopic()).isEqualTo(original.getTopic());
            assertThat(published.getPartitionKey()).isEqualTo(original.getPartitionKey());
            assertThat(published.getMessageId()).isEqualTo(original.getMessageId());
            assertThat(published.getPayload()).isEqualTo(original.getPayload());
            assertThat(published.getCreatedAt()).isEqualTo(original.getCreatedAt());
        }

        @Test
        @DisplayName("markPublished 호출 후에도 원본 인스턴스는 변경되지 않는다 (불변성 보장)")
        void markPublished_doesNotMutateOriginal() {
            // Given
            OutboxMessage original = OutboxMessage.create(
                    AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE,
                    TOPIC, PARTITION_KEY, MESSAGE_ID, PAYLOAD);

            // When
            OutboxMessage published = original.markPublished();

            // Then
            // 원본은 여전히 미발행 상태
            assertThat(original.isPublished()).isFalse();
            assertThat(original.getPublishedAt()).isNull();
            // 새 인스턴스는 별개 객체
            assertThat(published).isNotSameAs(original);
        }
    }
}
