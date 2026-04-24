package com.gearshow.backend.showcase.application.port.out;

/**
 * 워크플로우 단위 분산 락 포트 (설계 §6.1).
 *
 * <p>Worker 의 상태 전이 TX (TX1 / TX2 / TX_final) 를 보호해 다중 인스턴스 경합과 Reconcile
 * 와의 동시 접근을 직렬화한다. 락은 반드시 **TX 지속시간(ms 수준) 만** 보유하며, 외부 I/O
 * (S3, Tripo) 는 락 밖에서 수행한다 (설계 §6.2).</p>
 *
 * <p><b>TTL 전략</b>: 구현체는 lease(기본 10초) 를 걸고 watchdog 으로 연장한다. Worker 크래시
 * 시 TTL 만료 후 자연스럽게 해제되어 Reconcile 가 복구할 수 있다.</p>
 */
public interface WorkflowLockPort {

    /**
     * {@code withLock} 내부에서 실행되는 크리티컬 섹션. 체크드 예외를 던지지 않는다.
     */
    @FunctionalInterface
    interface LockedAction {
        void run();
    }

    /**
     * 지정 workflow 에 대한 락을 획득한 상태로 {@code action} 을 실행한다. 락 획득에 실패하면
     * 즉시 {@link WorkflowLockBusyException} 을 던진다 (대기 없음 — 다른 Worker/Reconcile 이
     * 처리 중이라는 신호로 해석).
     *
     * @param workflowId 락 스코프. Redis 키는 {@code workflow:lock:{workflowId}}
     * @param action     락 안에서 실행할 작업
     * @throws WorkflowLockBusyException 다른 호스트가 이미 락을 보유 중인 경우
     */
    void withLock(Long workflowId, LockedAction action);

    /**
     * 다른 Worker/Reconcile 이 이미 락을 보유해 즉시 획득이 불가능할 때 던진다.
     *
     * <p>이 경로는 "정상적인 경합" 이므로 호출자는 조용히 skip 하거나 관측 로그만 남긴다 —
     * 재시도는 Kafka 레벨에서 자연스럽게 이어진다.</p>
     */
    class WorkflowLockBusyException extends RuntimeException {
        public WorkflowLockBusyException(Long workflowId) {
            super("workflow lock busy - workflowId: " + workflowId);
        }
    }
}
