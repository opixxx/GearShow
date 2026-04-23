package com.gearshow.backend.showcase.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ModelGenerationWorkflowJpaRepository} 및 {@link ModelGenerationWorkflowJpaEntity} 통합 테스트.
 *
 * <p>신규 워크플로우 생성, UNIQUE 제약 (같은 idempotency_key 중복 금지),
 * 그리고 CurrentStep ENUM 문자열 저장 · 조회를 검증한다.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ModelGenerationWorkflowJpaRepositoryTest {

    @Autowired
    private ModelGenerationWorkflowJpaRepository repository;

    @Nested
    @DisplayName("저장 및 조회")
    class SaveAndFind {

        @Test
        @DisplayName("REQUESTED 상태로 생성하고 idempotency_key 로 조회한다")
        void save_and_findByIdempotencyKey() {
            ModelGenerationWorkflowJpaEntity entity = ModelGenerationWorkflowJpaEntity.requested(
                    10L, "idem-001", 1);

            repository.saveAndFlush(entity);

            Optional<ModelGenerationWorkflowJpaEntity> found =
                    repository.findByIdempotencyKey("idem-001");
            assertThat(found).isPresent();
            assertThat(found.get().getShowcaseId()).isEqualTo(10L);
            assertThat(found.get().getAttemptNo()).isEqualTo(1);
            assertThat(found.get().getCurrentStep())
                    .isEqualTo(ModelGenerationWorkflowJpaEntity.CurrentStep.REQUESTED);
            assertThat(found.get().getRetryCount()).isZero();
            assertThat(found.get().getCreatedAt()).isNotNull();
            assertThat(found.get().getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("존재하지 않는 idempotency_key 조회 시 빈 Optional")
        void findByIdempotencyKey_notFound_returnsEmpty() {
            assertThat(repository.findByIdempotencyKey("nonexistent")).isEmpty();
        }
    }

    @Nested
    @DisplayName("UNIQUE 제약")
    class UniqueConstraint {

        @Test
        @DisplayName("같은 idempotency_key 로 중복 생성 시 예외")
        void duplicateIdempotencyKey_fails() {
            repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(10L, "idem-dup", 1));

            assertThatThrownBy(() -> repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(11L, "idem-dup", 1)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("재시도 이력")
    class RetryHistory {

        @Test
        @DisplayName("같은 showcase_id 로 attempt_no 를 증가시켜 새 행을 저장할 수 있다")
        void sameShowcase_differentAttempt() {
            repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(20L, "idem-a", 1));
            repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(20L, "idem-b", 2));

            assertThat(repository.findAll())
                    .extracting(ModelGenerationWorkflowJpaEntity::getShowcaseId,
                                ModelGenerationWorkflowJpaEntity::getAttemptNo)
                    .contains(
                            org.assertj.core.groups.Tuple.tuple(20L, 1),
                            org.assertj.core.groups.Tuple.tuple(20L, 2)
                    );
        }
    }
}
