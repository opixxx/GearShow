package com.gearshow.backend.showcase.application.port.in;

/**
 * 3D 모델 생성 워크플로우 TX1 유스케이스.
 *
 * <p>Worker 가 Kafka 메시지 수신 후 호출한다. 본 PR(P1-D-α+β) 범위에서는
 * {@code REQUESTED → PREPARING} 원자적 상태 전이와 소스 이미지 S3 존재 검증까지만 수행한다.
 * Tripo upload/task 호출과 {@code PREPARING → GENERATING} 전이(TX2)는
 * P1-D-γ 에서 이어진다.</p>
 *
 * <p><b>설계 결정 #1 (PREPARING 선행 전환)</b>: Tripo 호출 전에 상태를 PREPARING 으로
 * 바꿔두어, Recovery 와 다른 Worker 가 끼어드는 것을 방지한다. 조건부 UPDATE 로 race 차단.</p>
 *
 * <p><b>설계 결정 #5 (예외 처리)</b>: 비즈니스 실패(이미지 누락 등)는 예외를 던지지 않고
 * {@code FAILED} 로 마킹하고 정상 반환한다. Kafka 재시도가 발생하지 않도록(멱등성 가드에
 * 막혀 무의미) 하는 원칙은 Tripo 도입 시점(P1-D-γ) 에 다시 강화된다.</p>
 */
public interface PrepareWorkflowUseCase {

    /**
     * TX1 을 수행한다. 워크플로우를 {@code REQUESTED → PREPARING} 으로 전이하고
     * S3 소스 이미지 존재를 검증한다.
     *
     * @param workflowId {@code model_generation_workflow} 행 ID
     */
    void prepare(Long workflowId);
}
