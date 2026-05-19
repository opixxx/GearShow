package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.application.dto.StuckWorkflow;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.dto.WorkflowSnapshot;
import com.gearshow.backend.showcase.application.dto.WorkflowStep;

import java.time.Instant;
import java.util.List;
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
     * 보상 전이 {@code PREPARING → REQUESTED} (ADR-026). Tripo 호출이 미과금 확정 retryable
     * 예외로 실패했을 때만 호출되어, {@code @RetryableTopic} 재발행분이 REQUESTED 를 보고
     * 실제 재시도하게 한다.
     *
     * <p>조건부 UPDATE (ADR-012). WHERE {@code current_step = PREPARING AND tripo_task_id IS NULL
     * AND NOT EXISTS tripo_pending_task} — 과금된 워크플로우를 되돌리지 않아 이중 과금을 차단한다
     * (ADR-011 §④). {@code started_at} 은 NULL 로 리셋한다.</p>
     *
     * @return 1=보상 성공(호출자가 원래 예외 재throw), 0=과금됨/이미 전이됨(재throw 금지)
     */
    int compensatePreparingToRequested(Long workflowId);

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

    /**
     * 워크플로우를 {@code PREPARING → GENERATING} 으로 전이하며 Tripo task 식별자를 저장한다.
     * 조건부 UPDATE (WHERE {@code current_step = PREPARING}) 로 race 를 차단하고,
     * {@code tripo_task_id} · {@code heartbeat_at} · {@code updated_at} 을 함께 갱신한다.
     *
     * <p>{@code tripo_trace_id} 는 현재 TripoApiClient 가 응답 헤더를 캡처하지 않으므로
     * null 로 유지. 후속 작업에서 X-Tripo-Trace-ID 헤더 파싱 추가 시 같은 메서드에 인자 확장.</p>
     *
     * @return 1=전이 성공, 0=이미 다른 상태로 이동했거나 존재하지 않음
     */
    int markGenerating(Long workflowId, String tripoTaskId);

    /**
     * Poller 가 Tripo 를 조회했음을 기록한다 (last_polled_at, heartbeat_at, updated_at 갱신).
     * WHERE {@code current_step = GENERATING AND tripo_succeeded_at IS NULL} 을 강제해
     * 이미 완료됐거나 종결된 워크플로우를 덮지 않는다.
     *
     * @return 1=성공, 0=skip 대상 (다른 주체가 전이)
     */
    int markPolled(Long workflowId);

    /**
     * Tripo SUCCESS 인지 시각을 기록한다. {@code current_step} 은 GENERATING 을 유지하고
     * {@code tripo_succeeded_at} 만 NOW() 로 채워 GENERATING 내부 서브-단계를 전환한다
     * (Tripo 처리 중 → S3 미러링 중, 설계 §5).
     *
     * <p>WHERE {@code current_step = GENERATING AND tripo_succeeded_at IS NULL} — 중복 이벤트 방지.</p>
     *
     * @return 1=성공 (이 시점에만 TripoSuccessEvent 발행), 0=이미 전이됨
     */
    int markTripoSucceeded(Long workflowId);

    /**
     * Downloader 의 TX_final — {@code GENERATING → COMPLETED} 전이. {@code finished_at} 도 함께
     * 기록한다. WHERE 절로 {@code current_step = GENERATING AND tripo_succeeded_at IS NOT NULL}
     * 을 강제해 Poller SUCCESS 인지 전 재진입과 이미 COMPLETED 된 워크플로우 재전이를 모두 차단한다.
     *
     * @return 1=전이 성공 (도메인 UPDATE + Outbox 발행 진행), 0=다른 Downloader 가 이미 처리
     */
    int markCompleted(Long workflowId);

    /**
     * 쇼케이스 ID 기준 가장 최근 {@code attempt_no} 워크플로우 스냅샷을 조회한다.
     * ADR-010 Q4-(1) — 쇼케이스 상세 응답의 3D 상태 유도에 사용. {@code uk_mgw_showcase_attempt}
     * UNIQUE 인덱스를 타도록 쿼리를 구성해야 한다.
     */
    Optional<WorkflowSnapshot> findLatestSnapshotByShowcaseId(Long showcaseId);

    /**
     * PREPARING 상태에서 {@code heartbeat_at} 이 임계 이전인 stuck 워크플로우를 조회한다.
     * Reconcile 배치 (설계 §8.4) 의 PREPARING 복구 대상.
     */
    List<StuckWorkflow> findStuckPreparing(Instant heartbeatBefore, int limit);

    /**
     * GENERATING 상태 + {@code tripo_succeeded_at IS NULL} + {@code last_polled_at} 임계 이전
     * stuck 을 조회한다 (Tripo 처리 중 폴링 유실).
     */
    List<StuckWorkflow> findStuckGeneratingTripo(Instant lastPolledBefore, int limit);

    /**
     * GENERATING 상태 + {@code tripo_succeeded_at IS NOT NULL} + {@code heartbeat_at} 임계 이전
     * stuck 을 조회한다 (S3 미러링 중 워커 사망).
     */
    List<StuckWorkflow> findStuckGeneratingS3(Instant heartbeatBefore, int limit);

    /**
     * REQUESTED 상태에서 {@code created_at} 이 임계 이전인 워크플로우를 경고 목적으로 조회한다.
     * Outbox Relay 점검 트리거.
     */
    List<StuckWorkflow> findStuckRequested(Instant createdBefore, int limit);

    /**
     * 현재 활성 생성 작업 수 = {@code current_step IN (PREPARING, GENERATING)} 인 워크플로우 수.
     *
     * <p>입장 게이트(ADR-025) 의 동시성 cap 판정 지표. DB 가 SoT 이므로 워커 사망/재시작에도
     * 누수 없이 정확하다. 선행 인덱스가 {@code current_step} 으로 시작하는 복합 인덱스
     * ({@code idx_mgw_step_*}) 를 활용해 카운트한다. check-then-act 가 원자적이지 않은 점은
     * ADR-025 가 soft cap 으로 수용한다.</p>
     */
    long countActive();
}
