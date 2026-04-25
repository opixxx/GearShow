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
     * <p>Outbox {@code event_id} / 메시지 {@code messageId} 는 {@code idempotencyKey} 로부터
     * 결정적 파생(ADR-011 ③) 된다 — 서버 UUID 금지. 재시도 호출은 같은 {@code idempotencyKey}
     * 를 그대로 사용해, Consumer 의 {@code processed_message} 중복 차단이 작동한다.</p>
     *
     * @param workflowId     {@code model_generation_workflow} 행 ID (ADR-010 프로세스 테이블)
     * @param showcaseId     쇼케이스 ID (파티션 키 + Outbox aggregate ID)
     * @param idempotencyKey API 멱등성 키. 해시 입력으로 사용
     */
    void publishRequested(Long workflowId, Long showcaseId, String idempotencyKey);

    /**
     * 3D 모델 생성이 최종 완료(COMPLETED) 되었음을 알리는 이벤트를 발행한다 (P1-F, 설계 §7 [8]).
     *
     * <p>TX_final 안에서 호출되어 "DB 커밋 = Kafka 발행 보장" 을 만족한다. 후속 Consumer
     * (FCM 푸시 등) 는 {@code workflowId} 기준으로 idempotent 처리.</p>
     *
     * @param workflowId        완료된 워크플로우 ID
     * @param showcaseId        대상 쇼케이스 ID (파티션 키)
     * @param modelFileUrl      S3 미러링된 GLB 파일 URL
     * @param previewImageUrl   S3 미러링된 프리뷰 이미지 URL (없으면 null)
     */
    void publishCompleted(Long workflowId,
                          Long showcaseId,
                          String modelFileUrl,
                          String previewImageUrl);
}
