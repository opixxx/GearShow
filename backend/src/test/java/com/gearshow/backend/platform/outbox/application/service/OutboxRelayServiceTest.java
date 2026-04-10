package com.gearshow.backend.platform.outbox.application.service;

import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.platform.outbox.infrastructure.config.OutboxRelayProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * OutboxRelayService 단위 테스트.
 *
 * <p>Relay 서비스는 Outbox 폴링 → Kafka 발행 → published 마킹 흐름을 담당한다.
 * 핵심 불변식은 "발행 성공한 메시지만 markPublished 된다" 이므로
 * 실패 시나리오에서 markPublished 가 호출되지 않는지를 중점 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    private static final int BATCH_SIZE = 100;
    private static final long PUBLISH_TIMEOUT_MS = 5_000L;

    @Mock
    private OutboxMessagePort outboxMessagePort;

    @Mock
    @SuppressWarnings("rawtypes")
    private KafkaTemplate outboxKafkaTemplate;

    private OutboxRelayService service;

    @BeforeEach
    void setUp() {
        OutboxRelayProperties properties = new OutboxRelayProperties(
                1_000L, BATCH_SIZE, PUBLISH_TIMEOUT_MS,
                "0 0 4 * * *", "Asia/Seoul", 7, 1_000, 50L);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, byte[]> typed = outboxKafkaTemplate;
        service = new OutboxRelayService(outboxMessagePort, typed, properties);
    }

    private static OutboxMessage pendingMessage(long id, String topic) {
        return OutboxMessage.builder()
                .id(id)
                .aggregateType("SHOWCASE_3D_MODEL")
                .aggregateId(id)
                .eventType("MODEL_GENERATION_REQUESTED")
                .topic(topic)
                .partitionKey(String.valueOf(id))
                .messageId("msg-" + id)
                .payload("{\"id\":" + id + "}")
                .published(false)
                .createdAt(java.time.Instant.now())
                .build();
        // 주: Builder 를 직접 쓰는 이유는 id 가 있는 상태로 생성해서 markPublished(id) 검증을 하기 위함
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, byte[]>> successFuture(String topic, int partition, long offset) {
        // SendResult.getRecordMetadata() 는 로그에서만 호출되지만 NPE 를 피하려면 값이 있어야 한다
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, partition), offset, 0, 0L, 0, 0);
        SendResult<String, byte[]> result = new SendResult<>(
                new ProducerRecord<>(topic, "key", new byte[]{0}), metadata);
        return CompletableFuture.completedFuture(result);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, byte[]>> failedFuture() {
        CompletableFuture<SendResult<String, byte[]>> future = new CompletableFuture<>();
        future.completeExceptionally(
                new ExecutionException("Kafka broker unreachable", new RuntimeException("boom")));
        return future;
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("pending 메시지가 여러 건이면 전부 발행하고 동일 횟수만큼 markPublished 가 호출된다")
        void publishPending_multipleMessages_allSucceed() {
            // Given
            List<OutboxMessage> pending = List.of(
                    pendingMessage(1L, "topic-a"),
                    pendingMessage(2L, "topic-a"),
                    pendingMessage(3L, "topic-b")
            );
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(pending);
            given(outboxKafkaTemplate.send(any(ProducerRecord.class)))
                    .willReturn(successFuture("topic-a", 0, 10L))
                    .willReturn(successFuture("topic-a", 0, 11L))
                    .willReturn(successFuture("topic-b", 1, 20L));

            // When
            int publishedCount = service.publishPending();

            // Then
            assertThat(publishedCount).isEqualTo(3);
            verify(outboxKafkaTemplate, times(3)).send(any(ProducerRecord.class));
            verify(outboxMessagePort, times(1)).markPublished(1L);
            verify(outboxMessagePort, times(1)).markPublished(2L);
            verify(outboxMessagePort, times(1)).markPublished(3L);
        }

        @Test
        @DisplayName("발행 시 메시지의 topic/partitionKey/payload 가 ProducerRecord 에 그대로 전달된다")
        void publishPending_buildsProducerRecordWithCorrectFields() {
            // Given
            OutboxMessage message = pendingMessage(42L, "showcase.topic");
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(List.of(message));
            given(outboxKafkaTemplate.send(any(ProducerRecord.class)))
                    .willReturn(successFuture("showcase.topic", 0, 5L));

            // When
            service.publishPending();

            // Then
            ArgumentCaptor<ProducerRecord<String, byte[]>> captor =
                    ArgumentCaptor.forClass(ProducerRecord.class);
            verify(outboxKafkaTemplate).send(captor.capture());
            ProducerRecord<String, byte[]> record = captor.getValue();
            assertThat(record.topic()).isEqualTo("showcase.topic");
            assertThat(record.key()).isEqualTo("42");
            assertThat(new String(record.value())).isEqualTo("{\"id\":42}");
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("pending 이 없으면 0을 반환하고 Kafka 발행을 시도하지 않는다")
        void publishPending_emptyBatch_returnsZero() {
            // Given
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(List.of());

            // When
            int publishedCount = service.publishPending();

            // Then
            assertThat(publishedCount).isZero();
            verify(outboxKafkaTemplate, never()).send(any(ProducerRecord.class));
            verify(outboxMessagePort, never()).markPublished(anyLong());
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("Kafka 발행이 실패한 메시지는 markPublished 되지 않아 다음 주기에 재시도된다")
        void publishPending_kafkaFailure_doesNotMarkPublished() {
            // Given
            OutboxMessage message = pendingMessage(7L, "topic-x");
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(List.of(message));
            given(outboxKafkaTemplate.send(any(ProducerRecord.class))).willReturn(failedFuture());

            // When
            int publishedCount = service.publishPending();

            // Then
            assertThat(publishedCount).isZero();
            verify(outboxMessagePort, never()).markPublished(anyLong());
        }

        @Test
        @DisplayName("일부는 성공 일부는 실패하면 성공한 메시지만 markPublished 된다")
        void publishPending_partialFailure_marksOnlySuccessful() {
            // Given
            List<OutboxMessage> pending = List.of(
                    pendingMessage(1L, "topic-a"),  // 성공
                    pendingMessage(2L, "topic-a"),  // 실패
                    pendingMessage(3L, "topic-a")   // 성공
            );
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(pending);
            given(outboxKafkaTemplate.send(any(ProducerRecord.class)))
                    .willReturn(successFuture("topic-a", 0, 1L))
                    .willReturn(failedFuture())
                    .willReturn(successFuture("topic-a", 0, 2L));

            // When
            int publishedCount = service.publishPending();

            // Then
            assertThat(publishedCount).isEqualTo(2);
            verify(outboxMessagePort, times(1)).markPublished(eq(1L));
            verify(outboxMessagePort, never()).markPublished(eq(2L));  // 실패한 것은 마킹 안 됨
            verify(outboxMessagePort, times(1)).markPublished(eq(3L));
        }
    }
}
