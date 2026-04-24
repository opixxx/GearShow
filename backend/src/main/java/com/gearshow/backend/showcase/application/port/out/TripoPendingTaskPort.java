package com.gearshow.backend.showcase.application.port.out;

import java.util.Optional;

/**
 * Tripo {@code POST /task} 성공 직후 {@code task_id} 를 선저장해 워커 크래시 시 유실을 방지하는
 * 포트 (ADR-011 ④ — Tripo 계층 이중 과금 방지).
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>{@link #preserve(Long, String)} — Tripo 가 task_id 를 반환한 직후 호출</li>
 *   <li>TX2({@code PREPARING → GENERATING}) 성공 시 {@link #deleteByWorkflowId(Long)} 으로 정리</li>
 *   <li>TX2 실패 · 워커 크래시 시엔 레코드가 남아 Reconcile(P1-G) 이 TX2 를 재개</li>
 * </ol>
 */
public interface TripoPendingTaskPort {

    /**
     * Tripo 응답 task_id 를 선저장한다. {@code workflow_id} 는 PK 이므로 동일 workflow 에 대한
     * 중복 호출은 {@code DataIntegrityViolationException} 으로 이어진다 — 정상 흐름에선 발생 불가.
     */
    void preserve(Long workflowId, String tripoTaskId);

    /**
     * TX2 성공 후 pending 레코드를 삭제한다. 존재하지 않으면 조용히 통과 (멱등).
     */
    void deleteByWorkflowId(Long workflowId);

    /**
     * Reconcile 이 PREPARING stuck 을 복구할 때 선저장된 {@code task_id} 를 복원한다.
     * 레코드가 없으면 Empty — 이 경우 task_id 가 유실된 상태이므로 FAILED 전이가 적절.
     */
    Optional<String> findTaskIdByWorkflowId(Long workflowId);
}
