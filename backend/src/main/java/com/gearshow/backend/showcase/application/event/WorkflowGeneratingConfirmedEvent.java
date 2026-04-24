package com.gearshow.backend.showcase.application.event;

/**
 * Worker 의 TX2 ({@code PREPARING → GENERATING}) 가 성공으로 확정되었을 때 발행되는
 * ApplicationEvent.
 *
 * <p>{@code PrepareWorkflowService} 가 조건부 UPDATE affected=1 + pending 정리 이후 락 밖에서
 * 발행한다. 리스너는 {@code @TransactionalEventListener(AFTER_COMMIT)} 로 Redis DelayedQueue 에
 * 워크플로우 id 를 offer 해 Poller 가 이어받도록 한다 (설계 §6.2 30s delay).</p>
 *
 * <p>ApplicationEvent 로 분리한 이유는 (1) Worker 서비스가 Redis Port 를 직접 의존하지 않게 하고,
 * (2) 향후 Worker 경로에 {@code @Transactional} 이 추가되어도 "DB 커밋 후 offer" 계약을 자연히
 * 만족시키기 위함이다.</p>
 */
public record WorkflowGeneratingConfirmedEvent(Long workflowId) {
}
