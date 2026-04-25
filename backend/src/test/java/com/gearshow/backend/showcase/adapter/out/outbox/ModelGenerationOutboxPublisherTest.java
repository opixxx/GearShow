package com.gearshow.backend.showcase.adapter.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * {@link ModelGenerationOutboxPublisher} 단위 테스트.
 *
 * <p>핵심 불변식: {@code event_id = messageId = SHA-256(idempotencyKey)} (ADR-011 ③).
 * 같은 idempotencyKey 로 반복 호출 시 동일 event_id 가 생성됨을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelGenerationOutboxPublisher")
class ModelGenerationOutboxPublisherTest {

    @Mock
    private OutboxMessagePort outboxMessagePort;

    private ObjectMapper objectMapper;
    private ModelGenerationOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new ModelGenerationOutboxPublisher(outboxMessagePort, objectMapper);
        given(outboxMessagePort.save(org.mockito.ArgumentMatchers.any(OutboxMessage.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("publishRequested 는 SHA-256(idempotencyKey) 로 event_id 를 결정적 파생한다")
    void publishRequested_derivesEventIdFromIdempotencyKey() throws Exception {
        String idempotencyKey = "user-provided-key-xyz";
        publisher.publishRequested(77L, 100L, idempotencyKey);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        org.mockito.Mockito.verify(outboxMessagePort).save(captor.capture());

        OutboxMessage saved = captor.getValue();
        assertThat(saved.getMessageId())
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(saved.getAggregateId()).isEqualTo(100L);

        ModelGenerationRequestMessage payload = objectMapper.readValue(
                saved.getPayload(), ModelGenerationRequestMessage.class);
        assertThat(payload.messageId()).isEqualTo(saved.getMessageId());
        assertThat(payload.workflowId()).isEqualTo(77L);
        assertThat(payload.showcaseId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("같은 idempotencyKey 로 재호출하면 같은 event_id 가 생성된다 (결정적 파생)")
    void publishRequested_sameKey_sameEventId() {
        publisher.publishRequested(1L, 100L, "same-key");
        publisher.publishRequested(2L, 100L, "same-key");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        org.mockito.Mockito.verify(outboxMessagePort, org.mockito.Mockito.times(2))
                .save(captor.capture());

        assertThat(captor.getAllValues().get(0).getMessageId())
                .isEqualTo(captor.getAllValues().get(1).getMessageId());
    }

    @Test
    @DisplayName("다른 idempotencyKey 는 다른 event_id 를 생성한다")
    void publishRequested_differentKeys_differentEventIds() {
        publisher.publishRequested(1L, 100L, "key-1");
        publisher.publishRequested(2L, 100L, "key-2");

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        org.mockito.Mockito.verify(outboxMessagePort, org.mockito.Mockito.times(2))
                .save(captor.capture());

        assertThat(captor.getAllValues().get(0).getMessageId())
                .isNotEqualTo(captor.getAllValues().get(1).getMessageId());
    }
}
