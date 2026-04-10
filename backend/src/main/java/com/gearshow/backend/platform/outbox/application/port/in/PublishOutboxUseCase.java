package com.gearshow.backend.platform.outbox.application.port.in;

/**
 * Outbox 메시지 발행 유스케이스 (Inbound Port).
 *
 * <p>외부 트리거(스케줄러)에 의해 호출되며, 아직 발행되지 않은
 * Outbox 메시지를 Kafka 로 전달하고 발행 완료 상태로 마킹한다.</p>
 */
public interface PublishOutboxUseCase {

    /**
     * 미발행 Outbox 메시지를 배치 단위로 발행한다.
     *
     * @return 이번 호출에서 실제 발행된 메시지 수
     */
    int publishPending();
}
