package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.adapter.out.persistence.ModelGenerationWorkflowJpaEntity;
import com.gearshow.backend.showcase.adapter.out.persistence.ModelGenerationWorkflowJpaRepository;
import com.gearshow.backend.showcase.adapter.out.persistence.TripoPendingTaskJpaEntity;
import com.gearshow.backend.showcase.adapter.out.persistence.TripoPendingTaskJpaRepository;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import java.time.Instant;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

/**
 * {@link PrepareWorkflowTxHelper} 통합 테스트 — TX 롤백 회귀 방지 (Bug C 의 본질).
 *
 * <p><b>왜 통합 테스트인가</b>: 단위 테스트는 호출 시퀀스만 검증한다. helper 를 별도 빈으로 뽑은
 * 핵심 이유는 "Spring AOP 프록시가 적용되어 {@code @Transactional} 이 실제로 동작" 하는 것을
 * 보장하기 위함이다. 만약 누군가 helper 를 같은 클래스 내부 메서드로 다시 합치거나, 호출처에서
 * 자기호출 패턴을 도입하면 단위 테스트는 통과해도 운영에선 트랜잭션 미적용 사고가 재발한다.
 * 이 테스트는 그 회귀를 차단한다 — 두 작업 중 하나라도 예외 시 다른 하나가 롤백되는지 확인.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class,
        PrepareWorkflowTxHelperIntegrationTest.MockTripoPendingTaskConfig.class})
@DisplayName("PrepareWorkflowTxHelper 통합 — TX 원자성")
class PrepareWorkflowTxHelperIntegrationTest {

    private static final String TRIPO_TASK_ID = "tripo-task-int-1";

    @Autowired
    private PrepareWorkflowTxHelper txHelper;

    @Autowired
    private ModelGenerationWorkflowJpaRepository workflowJpaRepository;

    @Autowired
    private TripoPendingTaskJpaRepository tripoPendingTaskJpaRepository;

    @Autowired
    private TripoPendingTaskPort tripoPendingTaskPortMock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long workflowId;

    @BeforeEach
    void setUp() {
        // 시드 작업은 명시적 TX 안에서 실행 (helper 의 @Transactional 이 자체 TX 를 시작하도록
        // 클래스 레벨 @Transactional 은 사용하지 않는다 — 그래야 롤백 검증이 의미를 가진다).
        workflowId = transactionTemplate.execute(status -> {
            ModelGenerationWorkflowJpaEntity entity = workflowJpaRepository.saveAndFlush(
                    ModelGenerationWorkflowJpaEntity.requested(
                            9001L, "tx-rollback-key-" + System.nanoTime(), 1));
            workflowJpaRepository.updateStepIfCurrent(
                    entity.getId(), WorkflowStep.REQUESTED, WorkflowStep.PREPARING, Instant.now());
            tripoPendingTaskJpaRepository.saveAndFlush(
                    TripoPendingTaskJpaEntity.preservingTaskId(entity.getId(), TRIPO_TASK_ID));
            return entity.getId();
        });

        Mockito.reset(tripoPendingTaskPortMock);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            tripoPendingTaskJpaRepository.deleteAll();
            workflowJpaRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("Happy: markGenerating + delete 가 단일 TX 안에서 모두 커밋된다")
    void executeTx2_happy_commitsBoth() {
        // 정상 mock — PortAdapter 의 실제 동작에 위임
        Mockito.doAnswer(inv -> {
            Long wfId = inv.getArgument(0);
            tripoPendingTaskJpaRepository.deleteByWorkflowIdIfExists(wfId);
            return null;
        }).when(tripoPendingTaskPortMock).deleteByWorkflowId(any());

        int affected = txHelper.executeTx2(workflowId, TRIPO_TASK_ID);

        assertThat(affected).isEqualTo(1);
        // workflow 가 GENERATING 으로 전이됐고 tripo_task_id 가 채워졌는지 확인
        ModelGenerationWorkflowJpaEntity reloaded =
                workflowJpaRepository.findById(workflowId).orElseThrow();
        assertThat(reloaded.getCurrentStep()).isEqualTo(WorkflowStep.GENERATING);
        assertThat(reloaded.getTripoTaskId()).isEqualTo(TRIPO_TASK_ID);
        // pending 행은 정리됨
        assertThat(tripoPendingTaskJpaRepository.findById(workflowId)).isEmpty();
    }

    @Test
    @DisplayName("롤백: deleteByWorkflowId 가 예외를 던지면 markGenerating 도 롤백되어 PREPARING 으로 복귀")
    void executeTx2_deleteThrows_rollbacksMarkGenerating() {
        willThrow(new DataAccessResourceFailureException("DELETE 실패 (시뮬레이션)"))
                .given(tripoPendingTaskPortMock).deleteByWorkflowId(any());

        assertThatThrownBy(() -> txHelper.executeTx2(workflowId, TRIPO_TASK_ID))
                .isInstanceOf(DataAccessResourceFailureException.class);

        // 핵심 검증: markGenerating 의 UPDATE 가 롤백되어 워크플로우는 PREPARING 그대로
        ModelGenerationWorkflowJpaEntity reloaded =
                workflowJpaRepository.findById(workflowId).orElseThrow();
        assertThat(reloaded.getCurrentStep())
                .as("delete 실패 시 markGenerating 도 같은 TX 안에서 롤백돼야 한다")
                .isEqualTo(WorkflowStep.PREPARING);
        assertThat(reloaded.getTripoTaskId())
                .as("tripo_task_id 는 GENERATING 전이와 함께 UPDATE 되므로 롤백 시 null 이어야 한다")
                .isNull();
        // 롤백되어 pending 행도 그대로 남아 있어야 한다 (Reconcile 이 정리하도록)
        assertThat(tripoPendingTaskJpaRepository.findById(workflowId)).isPresent();
    }

    /**
     * {@link TripoPendingTaskPort} 를 mock 으로 교체하여 예외 시뮬레이션을 가능하게 한다.
     * Persistence Adapter 의 실제 동작을 우회하지 않고 happy path 도 동일 빈으로 검증할 수 있도록
     * mock 안에서 실 Repository 위임도 가능 (위 테스트가 그 패턴 사용).
     */
    @TestConfiguration
    static class MockTripoPendingTaskConfig {
        @Bean
        @Primary
        TripoPendingTaskPort tripoPendingTaskPortMock() {
            return Mockito.mock(TripoPendingTaskPort.class);
        }
    }
}
