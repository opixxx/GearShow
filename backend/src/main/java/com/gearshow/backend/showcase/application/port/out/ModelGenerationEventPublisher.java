package com.gearshow.backend.showcase.application.port.out;

/**
 * 3D 모델 생성 요청 이벤트를 외부 전달(Outbox → Kafka) 하기 위한 Outbound Port.
 *
 * <p>Application 계층이 Kafka/직렬화 라이브러리/토픽 상수 같은 인프라 지식을 갖지 않도록
 * 포트 인터페이스로 추상화한다. 구현체는 {@code adapter/out/outbox} 에 위치한다.</p>
 *
 * <p>이 포트는 비즈니스 트랜잭션 안에서 호출되며, 구현체는 Outbox 테이블에 이벤트를
 * 기록하여 "DB 커밋 = 이후 Kafka 발행 보장" 을 제공한다.</p>
 */
public interface ModelGenerationEventPublisher {

    /**
     * 3D 모델 생성이 요청되었음을 알리는 이벤트를 발행한다.
     *
     * @param showcase3dModelId 3D 모델 ID (aggregate 식별자)
     * @param showcaseId        쇼케이스 ID (파티션 키)
     */
    void publishRequested(Long showcase3dModelId, Long showcaseId);
}
