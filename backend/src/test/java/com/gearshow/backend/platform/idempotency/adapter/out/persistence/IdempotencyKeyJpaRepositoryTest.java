package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link IdempotencyKeyJpaRepository} 및 {@link IdempotencyKeyJpaEntity} 통합 테스트.
 *
 * <p>UNIQUE 제약, 상태 전이 ({@code IN_PROGRESS} → {@code DONE}), 응답 캐싱 왕복을 검증한다.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IdempotencyKeyJpaRepositoryTest {

    @Autowired
    private IdempotencyKeyJpaRepository repository;

    @Nested
    @DisplayName("저장 및 조회")
    class SaveAndFind {

        @Test
        @DisplayName("IN_PROGRESS 상태로 저장하고 키로 조회한다")
        void save_and_findByKey() {
            IdempotencyKeyJpaEntity entity = IdempotencyKeyJpaEntity.createInProgress(
                    "key-001", 42L, Instant.now().plus(Duration.ofHours(24)));

            repository.saveAndFlush(entity);

            Optional<IdempotencyKeyJpaEntity> found = repository.findByIdempotencyKey("key-001");
            assertThat(found).isPresent();
            assertThat(found.get().getUserId()).isEqualTo(42L);
            assertThat(found.get().isDone()).isFalse();
            assertThat(found.get().getStatus()).isEqualTo(IdempotencyKeyJpaEntity.Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("존재하지 않는 키 조회 시 빈 Optional 을 반환한다")
        void findByKey_notFound_returnsEmpty() {
            assertThat(repository.findByIdempotencyKey("nonexistent")).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transition {

        @Test
        @DisplayName("markDone 호출 시 status=DONE 과 응답이 저장된다")
        void markDone_cachesResponse() {
            IdempotencyKeyJpaEntity entity = IdempotencyKeyJpaEntity.createInProgress(
                    "key-002", 42L, Instant.now().plus(Duration.ofHours(24)));
            repository.saveAndFlush(entity);

            entity.markDone(200, "{\"showcaseId\":1}");
            repository.saveAndFlush(entity);

            IdempotencyKeyJpaEntity reloaded = repository.findByIdempotencyKey("key-002").orElseThrow();
            assertThat(reloaded.isDone()).isTrue();
            assertThat(reloaded.getHttpStatus()).isEqualTo(200);
            assertThat(reloaded.getResponseBody()).contains("showcaseId");
        }
    }

    @Nested
    @DisplayName("UNIQUE 제약")
    class UniqueConstraint {

        @Test
        @DisplayName("같은 idempotency_key 로 중복 저장 시 DataIntegrityViolationException 발생")
        void duplicateKey_fails() {
            IdempotencyKeyJpaEntity first = IdempotencyKeyJpaEntity.createInProgress(
                    "key-dup", 1L, Instant.now().plus(Duration.ofHours(24)));
            repository.saveAndFlush(first);

            IdempotencyKeyJpaEntity duplicate = IdempotencyKeyJpaEntity.createInProgress(
                    "key-dup", 2L, Instant.now().plus(Duration.ofHours(24)));

            assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("만료")
    class Expiration {

        @Test
        @DisplayName("expires_at 이 null 이어도 저장 가능 (영구 보존 옵션)")
        void expiresAt_nullable() {
            IdempotencyKeyJpaEntity entity = IdempotencyKeyJpaEntity.createInProgress(
                    "key-003", 1L, null);

            IdempotencyKeyJpaEntity saved = repository.saveAndFlush(entity);
            assertThat(saved.getExpiresAt()).isNull();
        }
    }
}
