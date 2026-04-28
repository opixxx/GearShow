package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PrepareWorkflowService} 의 TX2 (PREPARING → GENERATING + tripo_pending_task DELETE) 를
 * 단일 트랜잭션으로 묶기 위한 헬퍼 빈 (설계 §7 [6]).
 *
 * <p><b>왜 별도 빈으로 분리했나</b>: {@code PrepareWorkflowService} 의 같은 클래스 내부에서
 * {@code @Transactional} 메서드를 호출하면 Spring AOP 프록시가 우회되어 트랜잭션이 적용되지
 * 않는다 (자기 호출 함정). 운영 사고 (2026-04-28) 에서 이 함정으로 인해
 * {@link TripoPendingTaskPort#deleteByWorkflowId} 호출 시 {@code TransactionRequiredException}
 * 이 발생했다. 헬퍼를 별도 빈으로 두면 호출이 Spring 컨테이너 경유 → 프록시 적용 → TX 보장.</p>
 *
 * <p><b>락 범위와의 관계</b>: 호출자({@code PrepareWorkflowService}) 가 분산 락 (Redisson)
 * 안에서 이 메서드를 호출한다. 락 → TX 시작 → 두 작업 → COMMIT → 락 해제 순서. 락은 TX
 * 지속시간(ms 수준) 만 보유한다.</p>
 */
@Service
@RequiredArgsConstructor
public class PrepareWorkflowTxHelper {

    private final ModelGenerationWorkflowPort workflowPort;
    private final TripoPendingTaskPort tripoPendingTaskPort;

    /**
     * TX2 를 단일 트랜잭션으로 수행한다.
     *
     * <ol>
     *   <li>{@code workflow.current_step} 를 {@code GENERATING} 으로 조건부 UPDATE — affected=1 이면 정상 전이,
     *       affected=0 이면 이미 다른 Worker/Reconcile 이 처리</li>
     *   <li>전이 성공 시 {@code tripo_pending_task} 행 삭제 (멱등) — 두 작업이 같은 TX 안에 있어
     *       부분 적용 위험 없음</li>
     * </ol>
     *
     * @return GENERATING 마킹 affected_rows. 0 이면 호출자는 정리 작업을 수행하지 않는다.
     */
    @Transactional
    public int executeTx2(Long workflowId, String tripoTaskId) {
        int affected = workflowPort.markGenerating(workflowId, tripoTaskId);
        if (affected == 0) {
            return 0;
        }
        tripoPendingTaskPort.deleteByWorkflowId(workflowId);
        return affected;
    }
}
