package com.gearshow.backend.platform.outbox.application.port.out;

/**
 * Outbox 메시지를 외부 메시지 브로커(Kafka 등)로 발행하는 Outbound Port.
 *
 * <p>application 계층이 Kafka/Spring 인프라 API(예: {@code KafkaTemplate}) 에
 * 직접 의존하지 않도록 추상화한다. 구현체는 {@code adapter/out/kafka} 같은 어댑터 계층에
 * 위치하여 실제 브로커 호출을 담당한다.</p>
 */
public interface OutboxMessageBroker {

    /**
     * 지정된 토픽으로 메시지를 동기 발행한다.
     *
     * <p>동기 발행으로 설계한 이유: Outbox Relay 가 발행 성공 여부를 확인한 뒤
     * {@code markPublished} 로 상태를 전이해야 하기 때문에, 비동기 콜백보다
     * 동기 대기가 트랜잭션 경계 관리 측면에서 단순하다.</p>
     *
     * @param topic        대상 Kafka 토픽
     * @param partitionKey 파티션 결정 키 (null 이면 라운드 로빈)
     * @param payload      발행할 바이트 페이로드 (이미 JSON 직렬화된 상태)
     * @param timeoutMs    ack 대기 타임아웃 (ms)
     * @throws BrokerPublishException 발행 실패 시
     */
    void publish(String topic, String partitionKey, byte[] payload, long timeoutMs);

    /**
     * 브로커 발행 실패를 나타내는 예외. 상위에서 catch 하여 retry 로직을 구성한다.
     */
    class BrokerPublishException extends RuntimeException {
        public BrokerPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
