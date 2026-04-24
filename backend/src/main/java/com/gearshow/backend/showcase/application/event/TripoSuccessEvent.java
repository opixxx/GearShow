package com.gearshow.backend.showcase.application.event;

/**
 * Tripo task 가 SUCCESS 로 확인된 시점에 발행되는 ApplicationEvent.
 *
 * <p>Poller 가 {@code markTripoSucceeded} affected=1 인 경우에만 한 번 발행한다.
 * 후속 단계(P1-F Downloader) 가 구독해 Tripo GET url → S3 mirror → TX_final
 * (GENERATING → COMPLETED) 을 수행한다.</p>
 *
 * <p>Spring {@code ApplicationEventPublisher} 를 경유해 동일 JVM 안에서 전달된다.
 * 분산 전파가 필요해지면 Outbox/Kafka 로 승격할 수 있다.</p>
 *
 * @param workflowId   {@code model_generation_workflow} 행 ID
 * @param tripoTaskId  성공이 확인된 Tripo task_id (다운로드 API 에 재사용)
 */
public record TripoSuccessEvent(Long workflowId, String tripoTaskId) {
}
