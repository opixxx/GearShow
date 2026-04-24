package com.gearshow.backend.showcase.application.port.in;

/**
 * 단일 워크플로우의 Tripo 상태 1회 조회 유스케이스 (Inbound Port).
 *
 * <p>Poller 스케줄러가 Redis DelayedQueue 에서 workflowId 를 꺼낼 때마다 호출한다.
 * 내부에서 Tripo semaphore 를 획득해 {@code getTask} 를 실행하고 phase 에 따라
 * DB 를 전이한다. "언제" 호출할지는 스케줄러(Adapter) 가, "무엇을" 할지는 이
 * 유스케이스(Application) 가 담당한다.</p>
 */
public interface PollWorkflowUseCase {

    /**
     * 워크플로우를 한 번 폴링한다.
     *
     * <ul>
     *   <li>IN_PROGRESS: {@code markPolled} + 다시 지연큐로 offer (호출자 또는 내부에서 재enqueue)</li>
     *   <li>SUCCESS: {@code markTripoSucceeded} affected=1 인 경우에만
     *       {@code TripoSuccessEvent} 를 발행</li>
     *   <li>FAILED/REJECTED: {@code markFailed(TRIPO_TASK_FAILED)}</li>
     * </ul>
     *
     * <p>세마포어 획득 실패({@code TripoSemaphoreTimeoutException}) 는 상위(Scheduler) 가
     * catch 해 짧은 delay 로 재enqueue 한다.</p>
     *
     * @param workflowId 폴링 대상 워크플로우 id
     * @return 폴링 결과 (재enqueue 필요 여부 판정용)
     */
    PollOutcome poll(Long workflowId);

    /** 단일 폴링 사이클의 결과 분기. */
    enum PollOutcome {
        /** 상태가 GENERATING 이 아니거나 이미 성공 마킹된 경우 — 재enqueue 불필요. */
        SKIPPED,
        /** Tripo 가 아직 처리 중 — 상위에서 재enqueue 해야 한다. */
        IN_PROGRESS,
        /** Tripo SUCCESS 이벤트까지 발행 완료. */
        SUCCEEDED,
        /** FAILED 마킹 완료 — 재enqueue 불필요. */
        FAILED
    }
}
