package com.gearshow.backend.showcase.adapter.out.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gearshow.backend.platform.outbox.application.port.in.PublishOutboxUseCase;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.infrastructure.config.ShowcaseKafkaTopicConfig;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox Relay → Kafka 발행 경로 통합 테스트.
 *
 * <p>P1-B-γ 가 도입한 결정적 {@code messageId = SHA-256(Idempotency-Key)} 와 {@code workflowId}
 * 페이로드가 실제 Kafka 발행을 왕복한 뒤에도 그대로 보존되는지 검증한다. Testcontainers 로
 * 실제 Confluent Kafka 브로커를 띄워 Mock 이 아닌 엔드-투-엔드 실물 발행을 확인한다.</p>
 */
// TestApiClient 가 TestRestTemplate 을 요구하므로 RANDOM_PORT 로 기동한다 (다른 통합 테스트와 동일).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("ModelGenerationOutbox → Kafka Relay 통합")
class ModelGenerationOutboxRelayIntegrationTest {

    private static final String CONFLUENT_KAFKA_IMAGE = "confluentinc/cp-kafka:7.6.1";

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse(CONFLUENT_KAFKA_IMAGE));

    /**
     * Kafka 통합 테스트 한정으로 {@code application-test.yml} 의 Kafka 비활성화를 재정의한다.
     * {@code spring.autoconfigure.exclude} 를 공백으로 덮어써 KafkaAutoConfiguration 을 활성화.
     */
    @DynamicPropertySource
    static void overrideKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.autoconfigure.exclude", () -> "");
    }

    @Autowired
    private ModelGenerationEventPublisher modelGenerationEventPublisher;

    @Autowired
    private PublishOutboxUseCase publishOutboxUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaConsumer<String, byte[]> consumer;

    @BeforeEach
    void subscribeConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(ShowcaseKafkaTopicConfig.MODEL_GENERATION_REQUEST_TOPIC));
    }

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close(Duration.ofSeconds(2));
        }
    }

    @Test
    @DisplayName("publishRequested 로 저장된 메시지가 Relay 에 의해 Kafka 에 발행되고 messageId·workflowId 가 보존된다")
    void happyPath_publishedMessageRetainsHash() throws Exception {
        // Given — 명시적 새 트랜잭션에서 Outbox INSERT 커밋
        String idempotencyKey = "it-happy-" + UUID.randomUUID();
        Long expectedWorkflowId = 11L;
        Long expectedShowcaseId = 33L;

        transactionTemplate.executeWithoutResult(status ->
                modelGenerationEventPublisher.publishRequested(
                        expectedWorkflowId,
                        expectedShowcaseId,
                        idempotencyKey));

        // When — Relay 수동 실행 (스케줄러 대기 없이)
        int published = publishOutboxUseCase.publishPending();
        assertThat(published).isGreaterThanOrEqualTo(1);

        // Then — Kafka 로부터 해당 showcaseId 의 레코드 수신 (offset 오염 방지용 필터)
        ConsumerRecord<String, byte[]> record = pollForKey(String.valueOf(expectedShowcaseId));
        assertThat(record.topic()).isEqualTo(ShowcaseKafkaTopicConfig.MODEL_GENERATION_REQUEST_TOPIC);

        ModelGenerationRequestMessage payload = objectMapper.readValue(
                record.value(), ModelGenerationRequestMessage.class);
        assertThat(payload.messageId())
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(payload.workflowId()).isEqualTo(expectedWorkflowId);
        assertThat(payload.showcaseId()).isEqualTo(expectedShowcaseId);
    }

    /**
     * 지정한 key 에 해당하는 레코드를 받을 때까지 최대 10초 폴링한다. 다른 key 의 레코드는
     * 테스트 간 offset 오염을 방지하기 위해 스킵한다 (동일 SpringContext 공유 시 필수).
     * 결정성(같은 idempotencyKey → 같은 messageId) 은 {@code ShowcaseWorkflowEventIdsTest}
     * 와 {@code ModelGenerationOutboxPublisherTest} 단위 테스트가 이미 커버한다.
     */
    private ConsumerRecord<String, byte[]> pollForKey(String expectedKey) {
        AtomicReference<ConsumerRecord<String, byte[]>> result = new AtomicReference<>();
        Awaitility.await("Kafka 로부터 key=" + expectedKey + " 레코드 수신")
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, byte[]> record : records) {
                        if (expectedKey.equals(record.key())) {
                            result.set(record);
                            return true;
                        }
                    }
                    return false;
                });
        return result.get();
    }
}
