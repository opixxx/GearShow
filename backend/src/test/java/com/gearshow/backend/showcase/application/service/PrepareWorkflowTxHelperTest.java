package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link PrepareWorkflowTxHelper} 단위 테스트.
 *
 * <p>TX2 의 두 작업 (markGenerating + deleteByWorkflowId) 이 단일 트랜잭션 내에서
 * 정의된 순서대로 실행되는지 검증한다. 트랜잭션 자체의 commit/rollback 은 Spring 컨테이너
 * 통합 테스트 영역이라 여기서는 호출 시퀀스만 단위 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrepareWorkflowTxHelper")
class PrepareWorkflowTxHelperTest {

    private static final Long WORKFLOW_ID = 42L;
    private static final String TRIPO_TASK_ID = "tripo-task-xyz";

    @Mock
    private ModelGenerationWorkflowPort workflowPort;
    @Mock
    private TripoPendingTaskPort tripoPendingTaskPort;

    @Test
    @DisplayName("affected=1: markGenerating 후 deleteByWorkflowId 가 같은 호출에서 순서대로 실행")
    void executeTx2_happy_callsMarkAndDeleteInOrder() {
        given(workflowPort.markGenerating(WORKFLOW_ID, TRIPO_TASK_ID)).willReturn(1);
        PrepareWorkflowTxHelper helper =
                new PrepareWorkflowTxHelper(workflowPort, tripoPendingTaskPort);

        int affected = helper.executeTx2(WORKFLOW_ID, TRIPO_TASK_ID);

        assertThat(affected).isEqualTo(1);
        InOrder inOrder = inOrder(workflowPort, tripoPendingTaskPort);
        inOrder.verify(workflowPort).markGenerating(WORKFLOW_ID, TRIPO_TASK_ID);
        inOrder.verify(tripoPendingTaskPort).deleteByWorkflowId(WORKFLOW_ID);
    }

    @Test
    @DisplayName("affected=0: deleteByWorkflowId 호출하지 않음 — pending 유지로 Reconcile 위임")
    void executeTx2_zero_skipsDelete() {
        given(workflowPort.markGenerating(WORKFLOW_ID, TRIPO_TASK_ID)).willReturn(0);
        PrepareWorkflowTxHelper helper =
                new PrepareWorkflowTxHelper(workflowPort, tripoPendingTaskPort);

        int affected = helper.executeTx2(WORKFLOW_ID, TRIPO_TASK_ID);

        assertThat(affected).isZero();
        verify(workflowPort, times(1)).markGenerating(WORKFLOW_ID, TRIPO_TASK_ID);
        verify(tripoPendingTaskPort, never()).deleteByWorkflowId(anyLong());
    }
}
