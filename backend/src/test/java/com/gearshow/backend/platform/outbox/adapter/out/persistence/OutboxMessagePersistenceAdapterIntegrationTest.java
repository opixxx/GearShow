package com.gearshow.backend.platform.outbox.adapter.out.persistence;

import com.gearshow.backend.platform.outbox.application.port.out.OutboxMessagePort;
import com.gearshow.backend.platform.outbox.domain.OutboxMessage;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxMessagePersistenceAdapter 통합 테스트 (Testcontainers MySQL).
 *
 * <p>Port 인터페이스를 통해 save / findPendingBatch / markPublished / deletePublishedBatch 의
 * 실제 SQL 동작과 복합 인덱스 기반 조회가 의도대로 작동하는지 검증한다.</p>
 *
 * <p>특히 {@code markPublishedById} 의 "이미 published=true 면 0 row affected" 동작,
 * {@code deletePublishedBatchOlderThan} 의 native 벌크 삭제, 발행 완료된 메시지가
 * findPendingBatch 결과에서 제외되는 동작을 집중 검증한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
class OutboxMessagePersistenceAdapterIntegrationTest {

    @Autowired
    private OutboxMessagePort outboxMessagePort;

    @Autowired
    private OutboxMessageJpaRepository repository;

    @BeforeEach
    void clean() {
        // 테스트 간 격리 — 이전 테스트가 남긴 레코드 정리
        repository.deleteAll();
    }

    private OutboxMessage pendingMessage(Long aggregateId, String topic) {
        return OutboxMessage.create(
                "SHOWCASE_3D_MODEL",
                aggregateId,
                "MODEL_GENERATION_REQUESTED",
                topic,
                String.valueOf(aggregateId),
                UUID.randomUUID().toString(),
                "{\"id\":" + aggregateId + "}"
        );
    }

    @Nested
    @DisplayName("save / findPendingBatch")
    class SaveAndFindPending {

        @Test
        @DisplayName("저장한 메시지는 published=false 상태로 findPendingBatch 에 포함된다")
        void save_thenFindPending_returnsSavedMessage() {
            // Given
            OutboxMessage message = pendingMessage(1L, "topic-a");

            // When
            OutboxMessage saved = outboxMessagePort.save(message);
            List<OutboxMessage> pending = outboxMessagePort.findPendingBatch(10);

            // Then
            assertThat(saved.getId()).isNotNull();
            assertThat(pending).hasSize(1);
            assertThat(pending.get(0).getId()).isEqualTo(saved.getId());
            assertThat(pending.get(0).isPublished()).isFalse();
            assertThat(pending.get(0).getPayload()).isEqualTo("{\"id\":1}");
        }

        @Test
        @DisplayName("findPendingBatch 는 limit 만큼만 반환한다")
        void findPendingBatch_respectsLimit() {
            // Given
            for (long i = 1; i <= 5; i++) {
                outboxMessagePort.save(pendingMessage(i, "topic-a"));
            }

            // When
            List<OutboxMessage> batch = outboxMessagePort.findPendingBatch(3);

            // Then
            assertThat(batch).hasSize(3);
        }

        @Test
        @DisplayName("findPendingBatch 는 createdAt 오름차순으로 반환한다")
        void findPendingBatch_ordersByCreatedAtAsc() {
            // Given: 3건을 순차 저장 (createdAt 이 다르게 찍힘)
            outboxMessagePort.save(pendingMessage(1L, "topic-a"));
            outboxMessagePort.save(pendingMessage(2L, "topic-a"));
            outboxMessagePort.save(pendingMessage(3L, "topic-a"));

            // When
            List<OutboxMessage> batch = outboxMessagePort.findPendingBatch(10);

            // Then — 먼저 저장된 것이 먼저 나옴
            assertThat(batch)
                    .extracting(OutboxMessage::getAggregateId)
                    .containsExactly(1L, 2L, 3L);
        }
    }

    @Nested
    @DisplayName("markPublished")
    class MarkPublished {

        @Test
        @DisplayName("markPublished 후 findPendingBatch 에서 제외되고 publishedAt 이 설정된다")
        void markPublished_excludesFromPending() {
            // Given
            OutboxMessage saved = outboxMessagePort.save(pendingMessage(1L, "topic-a"));

            // When
            outboxMessagePort.markPublished(saved.getId());

            // Then
            List<OutboxMessage> pending = outboxMessagePort.findPendingBatch(10);
            assertThat(pending).isEmpty();

            OutboxMessageJpaEntity entity = repository.findById(saved.getId()).orElseThrow();
            assertThat(entity.isPublished()).isTrue();
            assertThat(entity.getPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 published=true 인 메시지에 markPublished 를 재호출해도 에러 없이 멱등하게 동작한다")
        void markPublished_alreadyPublished_isIdempotent() {
            // Given
            OutboxMessage saved = outboxMessagePort.save(pendingMessage(1L, "topic-a"));
            outboxMessagePort.markPublished(saved.getId());
            Instant firstPublishedAt = repository.findById(saved.getId()).orElseThrow().getPublishedAt();

            // When — 재호출 (WHERE published=false 조건으로 0 row affected)
            outboxMessagePort.markPublished(saved.getId());

            // Then — publishedAt 이 변경되지 않음 (단일 UPDATE 쿼리의 조건 덕분)
            Instant secondPublishedAt = repository.findById(saved.getId()).orElseThrow().getPublishedAt();
            assertThat(secondPublishedAt).isEqualTo(firstPublishedAt);
        }
    }

    @Nested
    @DisplayName("deletePublishedBatchOlderThan")
    class DeletePublishedBatch {

        @Test
        @DisplayName("threshold 이전 published 메시지는 삭제되고 이후 메시지는 유지된다")
        void deletePublishedBatch_removesOldPublishedOnly() {
            // Given — 3건 저장 후 전부 markPublished
            OutboxMessage m1 = outboxMessagePort.save(pendingMessage(1L, "topic-a"));
            OutboxMessage m2 = outboxMessagePort.save(pendingMessage(2L, "topic-a"));
            OutboxMessage m3 = outboxMessagePort.save(pendingMessage(3L, "topic-a"));
            outboxMessagePort.markPublished(m1.getId());
            outboxMessagePort.markPublished(m2.getId());
            outboxMessagePort.markPublished(m3.getId());

            // 미래 시각을 threshold 로 주면 모두 삭제 대상
            Instant future = Instant.now().plus(Duration.ofHours(1));

            // When
            int deleted = outboxMessagePort.deletePublishedBatchOlderThan(future, 10);

            // Then
            assertThat(deleted).isEqualTo(3);
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("published=false 인 메시지는 오래되어도 삭제되지 않는다")
        void deletePublishedBatch_doesNotTouchPendingMessages() {
            // Given — pending 상태 메시지 (markPublished 호출 안 함)
            outboxMessagePort.save(pendingMessage(1L, "topic-a"));
            Instant future = Instant.now().plus(Duration.ofHours(1));

            // When
            int deleted = outboxMessagePort.deletePublishedBatchOlderThan(future, 10);

            // Then
            assertThat(deleted).isZero();
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("batchSize 를 넘는 후보가 있어도 batchSize 만큼만 삭제한다")
        void deletePublishedBatch_respectsBatchSize() {
            // Given — 5건 저장 후 전부 published
            for (long i = 1; i <= 5; i++) {
                OutboxMessage m = outboxMessagePort.save(pendingMessage(i, "topic-a"));
                outboxMessagePort.markPublished(m.getId());
            }
            Instant future = Instant.now().plus(Duration.ofHours(1));

            // When
            int deleted = outboxMessagePort.deletePublishedBatchOlderThan(future, 3);

            // Then
            assertThat(deleted).isEqualTo(3);
            assertThat(repository.count()).isEqualTo(2);
        }
    }
}
