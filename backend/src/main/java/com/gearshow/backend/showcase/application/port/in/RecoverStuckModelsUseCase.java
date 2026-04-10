package com.gearshow.backend.showcase.application.port.in;

/**
 * 좀비/stuck 상태의 3D 모델을 자동 복구하는 유스케이스.
 *
 * <p>다음 두 가지 케이스를 감지하고 처리한다:</p>
 * <ol>
 *   <li>REQUESTED 상태에서 오래 머물러 있는 경우 — Outbox 재등록을 통해 Worker 가 다시 집어가도록 한다.
 *       (Outbox 자체가 일반적인 유실을 막지만, Worker 가 tryAcquire 직후 크래시 같은 edge case 는 남는다.)</li>
 *   <li>GENERATING 상태인데 {@code generation_task_id} 가 비어있는 경우 — Worker 가 Tripo 호출 전/도중에
 *       멈춘 좀비이므로 자동 FAILED 처리한다.</li>
 * </ol>
 */
public interface RecoverStuckModelsUseCase {

    /**
     * 한 번의 복구 사이클을 실행한다.
     *
     * @return 이번 호출에서 복구(재발행 또는 FAILED 전환)된 모델 수
     */
    int recoverOnce();
}
