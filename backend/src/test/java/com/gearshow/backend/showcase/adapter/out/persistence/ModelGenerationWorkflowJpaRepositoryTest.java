package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
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
                    .isEqualTo(WorkflowStep.REQUESTED);
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

            // 다른 테스트 데이터로부터 격리: showcaseId=20 만 필터
            assertThat(repository.findAll().stream()
                    .filter(e -> e.getShowcaseId() == 20L)
                    .toList())
                    .extracting(ModelGenerationWorkflowJpaEntity::getAttemptNo)
                    .containsExactlyInAnyOrder(1, 2);
        }

        @Test
        @DisplayName("findTopByShowcaseIdOrderByAttemptNoDesc 는 가장 큰 attempt_no 행을 반환한다")
        void findTopByShowcaseId_returnsLatest() {
            repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(30L, "idem-x1", 1));
            repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(30L, "idem-x2", 2));
            repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(30L, "idem-x3", 5));

            Optional<ModelGenerationWorkflowJpaEntity> latest =
                    repository.findTopByShowcaseIdOrderByAttemptNoDesc(30L);

            assertThat(latest).isPresent();
            assertThat(latest.get().getAttemptNo()).isEqualTo(5);
            assertThat(latest.get().getIdempotencyKey()).isEqualTo("idem-x3");
        }

        @Test
        @DisplayName("findTopByShowcaseIdOrderByAttemptNoDesc 는 이력이 없으면 빈 Optional")
        void findTopByShowcaseId_noHistory_returnsEmpty() {
            assertThat(repository.findTopByShowcaseIdOrderByAttemptNoDesc(999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("상태 전이 (ADR-012 조건부 UPDATE)")
    class StateTransition {

        @Test
        @DisplayName("REQUESTED → PREPARING 조건부 UPDATE 는 affected=1 반환하고 started_at 을 초기화한다")
        void updateStepIfCurrent_fromRequested_succeeds() {
            ModelGenerationWorkflowJpaEntity saved = repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(40L, "idem-40a", 1));
            assertThat(saved.getStartedAt()).isNull();

            int affected = repository.updateStepIfCurrent(
                    saved.getId(), WorkflowStep.REQUESTED, WorkflowStep.PREPARING, Instant.now());

            assertThat(affected).isEqualTo(1);
            ModelGenerationWorkflowJpaEntity reloaded = repository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getCurrentStep()).isEqualTo(WorkflowStep.PREPARING);
            assertThat(reloaded.getStartedAt()).isNotNull();
            assertThat(reloaded.getHeartbeatAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 PREPARING 인 행에 REQUESTED→PREPARING 을 재호출하면 affected=0")
        void updateStepIfCurrent_alreadyTransitioned_returnsZero() {
            ModelGenerationWorkflowJpaEntity saved = repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(41L, "idem-41a", 1));
            repository.updateStepIfCurrent(
                    saved.getId(), WorkflowStep.REQUESTED, WorkflowStep.PREPARING, Instant.now());

            int affected = repository.updateStepIfCurrent(
                    saved.getId(), WorkflowStep.REQUESTED, WorkflowStep.PREPARING, Instant.now());

            assertThat(affected).isZero();
        }

        @Test
        @DisplayName("markFailed 는 FAILED 로 전이 + failure_* 를 채운다")
        void markFailed_fromPreparing_succeeds() {
            ModelGenerationWorkflowJpaEntity saved = repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(42L, "idem-42a", 1));
            repository.updateStepIfCurrent(
                    saved.getId(), WorkflowStep.REQUESTED, WorkflowStep.PREPARING, Instant.now());

            int affected = repository.markFailed(
                    saved.getId(),
                    WorkflowFailureCode.S3_KEY_MISSING.name(),
                    "test failure",
                    "S3",
                    Instant.now());

            assertThat(affected).isEqualTo(1);
            ModelGenerationWorkflowJpaEntity reloaded = repository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getCurrentStep()).isEqualTo(WorkflowStep.FAILED);
            assertThat(reloaded.getFailureCode()).isEqualTo("S3_KEY_MISSING");
            assertThat(reloaded.getFailureMessage()).isEqualTo("test failure");
            assertThat(reloaded.getFailureSource()).isEqualTo("S3");
            assertThat(reloaded.getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 FAILED 인 행에 markFailed 재호출하면 affected=0 (덮어쓰기 방지)")
        void markFailed_alreadyFailed_returnsZero() {
            ModelGenerationWorkflowJpaEntity saved = repository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(43L, "idem-43a", 1));
            repository.markFailed(saved.getId(),
                    WorkflowFailureCode.S3_KEY_MISSING.name(), "first", "S3", Instant.now());

            int affected = repository.markFailed(saved.getId(),
                    WorkflowFailureCode.SOURCE_IMAGES_MISSING.name(), "second", "INTERNAL", Instant.now());

            assertThat(affected).isZero();
            ModelGenerationWorkflowJpaEntity reloaded = repository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.getFailureCode()).isEqualTo("S3_KEY_MISSING"); // 원본 유지
        }
    }
}
