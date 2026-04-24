package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.exception.ConcurrentModelRetryException;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
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

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ModelGenerationWorkflowPersistenceAdapter} 통합 테스트 (Testcontainers MySQL).
 *
 * <p>Port 계약을 통해 조건부 UPDATE 의 실제 SQL 동작과 {@code @Modifying
 * (clearAutomatically, flushAutomatically)} 거동을 검증한다. 특히 ADR-012 에 따라 모든
 * 상태 전이가 WHERE 절 기반 race 차단을 수행하는지, {@code markTripoSucceeded} 가
 * 중복 이벤트 방지를 위해 {@code tripo_succeeded_at IS NULL} 조건을 지키는지 확인한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@DisplayName("ModelGenerationWorkflowPersistenceAdapter 통합")
class ModelGenerationWorkflowPersistenceAdapterIntegrationTest {

    private static final String TASK_ID = "tripo-task-abc";

    @Autowired
    private ModelGenerationWorkflowPort workflowPort;

    @Autowired
    private ModelGenerationWorkflowJpaRepository repository;

    /**
     * 테스트 간 deleteAll 타이밍/캐시 이슈로 UNIQUE 충돌이 간헐적으로 발생할 수 있어,
     * 테스트마다 nanoTime 기반 고유 showcaseId 를 발급한다. BIGINT 라 overflow 없음.
     */
    private static final AtomicLong SEED = new AtomicLong(0);

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private long nextShowcaseId() {
        return (System.nanoTime() & 0x7FFF_FFFF_FFFFL)
                + SEED.incrementAndGet() * 10_000L;
    }

    private long saveRequested(long showcaseId) {
        return workflowPort.saveRequested(showcaseId, "it-" + UUID.randomUUID(), 1);
    }

    @Nested
    @DisplayName("saveRequested / nextAttemptNo / findSnapshot")
    class SaveAndLookup {

        @Test
        @DisplayName("saveRequested 로 생성된 워크플로우는 REQUESTED 상태의 snapshot 을 반환한다")
        void saveRequestedCreatesRequestedSnapshot() {
            long showcaseId = nextShowcaseId();
            long id = saveRequested(showcaseId);

            Optional<WorkflowSnapshot> found = workflowPort.findSnapshot(id);

            assertThat(found).isPresent();
            WorkflowSnapshot s = found.orElseThrow();
            assertThat(s.id()).isEqualTo(id);
            assertThat(s.showcaseId()).isEqualTo(showcaseId);
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.REQUESTED);
            assertThat(s.attemptNo()).isEqualTo(1);
            assertThat(s.tripoTaskId()).isNull();
            assertThat(s.tripoSucceededAt()).isNull();
            assertThat(s.startedAt()).isNull();
            // heartbeatAt 은 REQUESTED 단계에선 아직 설정되지 않음 (첫 TX1 전이 시점부터 기록)
            assertThat(s.heartbeatAt()).isNull();
        }

        @Test
        @DisplayName("nextAttemptNo: 이력이 없으면 1, 있으면 max+1")
        void nextAttemptNoCalculatesCorrectly() {
            long showcaseId = nextShowcaseId();
            assertThat(workflowPort.nextAttemptNo(showcaseId)).isEqualTo(1);

            workflowPort.saveRequested(showcaseId, "k-1", 1);
            assertThat(workflowPort.nextAttemptNo(showcaseId)).isEqualTo(2);

            workflowPort.saveRequested(showcaseId, "k-2", 2);
            assertThat(workflowPort.nextAttemptNo(showcaseId)).isEqualTo(3);
        }

        @Test
        @DisplayName("같은 (showcaseId, attemptNo) 중복 저장 시 ConcurrentModelRetryException")
        void duplicateShowcaseAttempt_throwsConcurrentRetry() {
            long showcaseId = nextShowcaseId();
            workflowPort.saveRequested(showcaseId, "k-dup-1", 1);

            assertThatThrownBy(() -> workflowPort.saveRequested(showcaseId, "k-dup-2", 1))
                    .isInstanceOf(ConcurrentModelRetryException.class);
        }

        @Test
        @DisplayName("존재하지 않는 id 의 findSnapshot 은 Optional.empty")
        void findSnapshotMissing_returnsEmpty() {
            assertThat(workflowPort.findSnapshot(999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateStepIfCurrent — ADR-012 조건부 전이")
    class UpdateStepIfCurrent {

        @Test
        @DisplayName("REQUESTED→PREPARING: affected=1 반환, startedAt 이 설정된다")
        void requestedToPreparing_setsStartedAt() {
            long id = saveRequested(nextShowcaseId());

            int affected = workflowPort.updateStepIfCurrent(
                    id, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);

            assertThat(affected).isEqualTo(1);
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.PREPARING);
            assertThat(s.startedAt()).isNotNull();
        }

        @Test
        @DisplayName("기대 상태와 현재 상태가 다르면 affected=0, 상태는 변경되지 않는다")
        void mismatchedExpected_returnsZero() {
            long id = saveRequested(nextShowcaseId());

            int affected = workflowPort.updateStepIfCurrent(
                    id, WorkflowStep.PREPARING, WorkflowStep.GENERATING);

            assertThat(affected).isZero();
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.REQUESTED);
        }
    }

    @Nested
    @DisplayName("markGenerating — TX2")
    class MarkGenerating {

        @Test
        @DisplayName("PREPARING→GENERATING: tripoTaskId 가 저장되고 snapshot 에 반영된다")
        void preparingToGenerating_storesTaskId() {
            long id = saveRequested(nextShowcaseId());
            workflowPort.updateStepIfCurrent(id, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);

            int affected = workflowPort.markGenerating(id, TASK_ID);

            assertThat(affected).isEqualTo(1);
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.GENERATING);
            assertThat(s.tripoTaskId()).isEqualTo(TASK_ID);
            assertThat(s.tripoSucceededAt()).isNull();
        }

        @Test
        @DisplayName("REQUESTED 에서 바로 markGenerating 호출 시 affected=0 (WHERE 보호)")
        void fromRequested_returnsZero() {
            long id = saveRequested(nextShowcaseId());

            int affected = workflowPort.markGenerating(id, TASK_ID);

            assertThat(affected).isZero();
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.REQUESTED);
            assertThat(s.tripoTaskId()).isNull();
        }
    }

    @Nested
    @DisplayName("markPolled / markTripoSucceeded — Poller 경로")
    class PollerTransitions {

        private long prepareGeneratingWorkflow() {
            long id = saveRequested(nextShowcaseId());
            workflowPort.updateStepIfCurrent(id, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);
            workflowPort.markGenerating(id, TASK_ID);
            return id;
        }

        @Test
        @DisplayName("markPolled: GENERATING + tripo_succeeded_at IS NULL 이면 affected=1")
        void markPolledOnFreshGenerating_returnsOne() {
            long id = prepareGeneratingWorkflow();

            int affected = workflowPort.markPolled(id);

            assertThat(affected).isEqualTo(1);
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.GENERATING);
        }

        @Test
        @DisplayName("markPolled: 이미 markTripoSucceeded 된 상태면 affected=0")
        void markPolledAfterTripoSucceeded_returnsZero() {
            long id = prepareGeneratingWorkflow();
            workflowPort.markTripoSucceeded(id);

            int affected = workflowPort.markPolled(id);

            assertThat(affected).isZero();
        }

        @Test
        @DisplayName("markPolled: 아직 GENERATING 이 아닌 워크플로우는 affected=0")
        void markPolledOnRequested_returnsZero() {
            long id = saveRequested(nextShowcaseId());

            int affected = workflowPort.markPolled(id);

            assertThat(affected).isZero();
        }

        @Test
        @DisplayName("markTripoSucceeded: GENERATING 상태에서 첫 호출은 affected=1 + tripoSucceededAt 설정")
        void markTripoSucceededFirst_returnsOne() {
            long id = prepareGeneratingWorkflow();

            int affected = workflowPort.markTripoSucceeded(id);

            assertThat(affected).isEqualTo(1);
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.GENERATING);
            assertThat(s.tripoSucceededAt()).isNotNull();
        }

        @Test
        @DisplayName("markTripoSucceeded: 두 번째 호출은 affected=0 (중복 이벤트 방지)")
        void markTripoSucceededSecond_returnsZero() {
            long id = prepareGeneratingWorkflow();
            workflowPort.markTripoSucceeded(id);

            int affected = workflowPort.markTripoSucceeded(id);

            assertThat(affected).isZero();
        }

        @Test
        @DisplayName("markTripoSucceeded: PREPARING 상태에서는 affected=0")
        void markTripoSucceededOnPreparing_returnsZero() {
            long id = saveRequested(nextShowcaseId());
            workflowPort.updateStepIfCurrent(id, WorkflowStep.REQUESTED, WorkflowStep.PREPARING);

            int affected = workflowPort.markTripoSucceeded(id);

            assertThat(affected).isZero();
        }
    }

    @Nested
    @DisplayName("markFailed")
    class MarkFailed {

        @Test
        @DisplayName("REQUESTED 상태에서 markFailed: affected=1, currentStep=FAILED")
        void markFailedFromRequested() {
            long id = saveRequested(nextShowcaseId());

            int affected = workflowPort.markFailed(
                    id, WorkflowFailureCode.S3_KEY_MISSING, "S3 객체 누락", "S3");

            assertThat(affected).isEqualTo(1);
            WorkflowSnapshot s = workflowPort.findSnapshot(id).orElseThrow();
            assertThat(s.currentStep()).isEqualTo(WorkflowStep.FAILED);
        }

        @Test
        @DisplayName("이미 FAILED 상태에 markFailed 재시도: affected=0 (덮어쓰기 방지)")
        void markFailedTwice_secondReturnsZero() {
            long id = saveRequested(nextShowcaseId());
            workflowPort.markFailed(
                    id, WorkflowFailureCode.S3_KEY_MISSING, "첫 실패", "S3");

            int affected = workflowPort.markFailed(
                    id, WorkflowFailureCode.TRIPO_TASK_FAILED, "두 번째 시도", "TRIPO_API");

            assertThat(affected).isZero();
        }
    }
}
