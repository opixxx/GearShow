package com.gearshow.backend.platform.outbox.application.service;

import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessageBroker;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessageBroker.BrokerPublishException;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.platform.outbox.infrastructure.config.OutboxRelayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * OutboxRelayService 단위 테스트.
 *
 * <p>Relay 서비스는 Outbox 폴링 → 브로커 발행 → published 마킹 흐름을 담당한다.
 * 핵심 불변식은 "발행 성공한 메시지만 markPublished 된다" 이므로
 * 실패 시나리오에서 markPublished 가 호출되지 않는지를 중점 검증한다.</p>
 *
 * <p>Kafka 세부사항은 {@link OutboxMessageBroker} 포트 뒤에 숨어있어, 이 테스트는
 * Kafka API 를 직접 mock 하지 않고 포트만 mock 한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    private static final int BATCH_SIZE = 100;
    private static final long PUBLISH_TIMEOUT_MS = 5_000L;

    @Mock
    private OutboxMessagePort outboxMessagePort;

    @Mock
    private OutboxMessageBroker outboxMessageBroker;

    private OutboxRelayService service;

    @BeforeEach
    void setUp() {
        OutboxRelayProperties properties = new OutboxRelayProperties(
                1_000L, BATCH_SIZE, PUBLISH_TIMEOUT_MS,
                "0 0 4 * * *", "Asia/Seoul", 7, 1_000, 50L);
        service = new OutboxRelayService(outboxMessagePort, outboxMessageBroker, properties);
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
            willDoNothing().given(outboxMessageBroker)
                    .publish(any(String.class), any(String.class), any(byte[].class), anyLong());

            // When
            int publishedCount = service.publishPending();

            // Then
            assertThat(publishedCount).isEqualTo(3);
            verify(outboxMessageBroker, times(3))
                    .publish(any(String.class), any(String.class), any(byte[].class), anyLong());
            verify(outboxMessagePort, times(1)).markPublished(1L);
            verify(outboxMessagePort, times(1)).markPublished(2L);
            verify(outboxMessagePort, times(1)).markPublished(3L);
        }

        @Test
        @DisplayName("발행 시 메시지의 topic/partitionKey/payload 가 broker 포트에 그대로 전달된다")
        void publishPending_passesCorrectFieldsToBroker() {
            // Given
            OutboxMessage message = pendingMessage(42L, "showcase.topic");
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(List.of(message));
            willDoNothing().given(outboxMessageBroker)
                    .publish(any(String.class), any(String.class), any(byte[].class), anyLong());

            // When
            service.publishPending();

            // Then
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<byte[]> payloadCaptor = ArgumentCaptor.forClass(byte[].class);
            verify(outboxMessageBroker).publish(
                    topicCaptor.capture(), keyCaptor.capture(),
                    payloadCaptor.capture(), anyLong());
            assertThat(topicCaptor.getValue()).isEqualTo("showcase.topic");
            assertThat(keyCaptor.getValue()).isEqualTo("42");
            assertThat(new String(payloadCaptor.getValue())).isEqualTo("{\"id\":42}");
        }
    }

    @Nested
    @DisplayName("Empty")
    class Empty {

        @Test
        @DisplayName("pending 이 없으면 0을 반환하고 브로커 발행을 시도하지 않는다")
        void publishPending_emptyBatch_returnsZero() {
            // Given
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(List.of());

            // When
            int publishedCount = service.publishPending();

            // Then
            assertThat(publishedCount).isZero();
            verify(outboxMessageBroker, never())
                    .publish(any(String.class), any(String.class), any(byte[].class), anyLong());
            verify(outboxMessagePort, never()).markPublished(anyLong());
        }
    }

    @Nested
    @DisplayName("Unhappy Path")
    class UnhappyPath {

        @Test
        @DisplayName("브로커 발행이 실패한 메시지는 markPublished 되지 않아 다음 주기에 재시도된다")
        void publishPending_brokerFailure_doesNotMarkPublished() {
            // Given
            OutboxMessage message = pendingMessage(7L, "topic-x");
            given(outboxMessagePort.findPendingBatch(BATCH_SIZE)).willReturn(List.of(message));
            willThrow(new BrokerPublishException("Kafka broker unreachable", new RuntimeException("boom")))
                    .given(outboxMessageBroker)
                    .publish(any(String.class), any(String.class), any(byte[].class), anyLong());

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
            willDoNothing()
                    .willThrow(new BrokerPublishException("boom", new RuntimeException()))
                    .willDoNothing()
                    .given(outboxMessageBroker)
                    .publish(any(String.class), any(String.class), any(byte[].class), anyLong());

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
