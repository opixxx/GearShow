package com.gearshow.backend.showcase.application.port.in;

/**
 * Stuck 워크플로우 복구 유스케이스 (설계 §8.4).
 *
 * <p>Reconcile 스케줄러가 주기적으로 호출해 PREPARING / GENERATING (Tripo/S3 서브-단계) / REQUESTED
 * stuck 을 감지하고 복구한다. 반환 값은 관찰/알림 목적의 복구 시도 카운트이며, 실제 복구 여부
 * (affected=1 전이) 는 조건부 UPDATE 반환값으로만 확인된다.</p>
 */
public interface ReconcileStuckWorkflowsUseCase {

    /**
     * 한 사이클의 Reconcile 을 수행한다.
     *
     * @return 이 사이클에서 복구 시도한 워크플로우 수 (stuck 조회 총합)
     */
    int reconcileOnce();
}
