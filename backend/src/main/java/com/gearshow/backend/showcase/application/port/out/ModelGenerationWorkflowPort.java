package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;

import java.util.Optional;

/**
 * 3D 모델 생성 워크플로우(프로세스 테이블) 저장 포트.
 *
 * <p>도메인/프로세스 테이블 분리(ADR-010) 에 따라, 파이프라인 생명주기를 담당하는
 * {@code model_generation_workflow} 테이블에 대한 application 계층의 접근점이다.
 * 도메인 모델 승격은 Phase 1-D 의 후속 sub-phase 에서 처리되며, 이번 Phase 는 JPA 엔티티를
 * 어댑터에 감추고 "생성 요청 기록 + 상태 전이" 만 노출한다.</p>
 *
 * <p><b>상태 전이 원칙 (ADR-012)</b>: 모든 상태 전이는 조건부 UPDATE 로 수행되며
 * affected row 수로 성공 여부를 판정한다. Java 레벨 check-then-act 금지.</p>
 */
public interface ModelGenerationWorkflowPort {

    /**
     * 지정 쇼케이스에 대한 신규 워크플로우를 {@code REQUESTED} 상태로 기록한다.
     *
     * @param showcaseId     쇼케이스 ID
     * @param idempotencyKey API {@code Idempotency-Key} 헤더 값 (ADR-011 ①,
     *                       {@code uk_mgw_idempotency_key} UNIQUE 식별자로 사용)
     * @param attemptNo      재시도 순번. 신규 생성은 {@code 1}, 재시도는 {@link #nextAttemptNo(Long)} 의 반환값
     * @return 영속화된 워크플로우 ID
     */
    long saveRequested(Long showcaseId, String idempotencyKey, int attemptNo);

    /**
     * 재시도를 위해 다음 {@code attempt_no} 를 계산한다.
     *
     * <p>해당 {@code showcaseId} 에 대한 가장 큰 {@code attempt_no} + 1 을 반환한다.
     * 이력이 없으면 {@code 1}. 조회는 {@code idx_mgw_showcase_attempt} 인덱스를 사용.</p>
     */
    int nextAttemptNo(Long showcaseId);

    /**
     * 워크플로우의 읽기 전용 스냅샷을 조회한다. Worker/Poller 가 현재 상태를 파악하거나
     * 로그를 남길 때 사용한다.
     */
    Optional<WorkflowSnapshot> findSnapshot(Long workflowId);

    /**
     * 워크플로우의 {@code current_step} 을 조건부로 전이한다 (ADR-012). WHERE 에
     * {@code current_step = :expected} 를 걸어, 다른 Worker/Recovery 가 이미 전이했거나
     * 상태가 기대와 다르면 {@code 0} 을 반환한다.
     *
     * <p>함께 갱신:</p>
     * <ul>
     *   <li>{@code updated_at = NOW()}</li>
     *   <li>{@code heartbeat_at = NOW()} (stuck 판정 리셋)</li>
     *   <li>REQUESTED → PREPARING 전이 시에만 {@code started_at = NOW()} (최초 시작 시각 고정)</li>
     * </ul>
     *
     * @return 1=전이 성공, 0=상태 불일치(이미 전환되었거나 존재하지 않음)
     */
    int updateStepIfCurrent(Long workflowId, WorkflowStep expected, WorkflowStep next);

    /**
     * 워크플로우를 {@code FAILED} 로 마킹한다. {@code failure_code}, {@code failure_message},
     * {@code failure_source} 를 함께 기록하고 {@code finished_at = NOW()}.
     *
     * <p>WHERE 조건으로 {@code current_step NOT IN (COMPLETED, FAILED)} 를 강제해 이미 종료된
     * 워크플로우를 덮어쓰지 않는다.</p>
     *
     * @param source 실패 원천. 허용 값: {@code TRIPO_API}, {@code S3}, {@code NETWORK},
     *               {@code SCHEDULER}, {@code INTERNAL}
     * @return 1=마킹 성공, 0=이미 종료되었거나 존재하지 않음
     */
    int markFailed(Long workflowId, WorkflowFailureCode code, String message, String source);
}
