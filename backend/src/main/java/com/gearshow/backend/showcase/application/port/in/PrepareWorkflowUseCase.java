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

    /**
     * 입장 큐 drainer 가 재발행한 메시지의 진입점 (ADR-025).
     *
     * <p>{@link #prepare(Long)} 와 달리 <b>admission 게이트를 타지 않는다</b> — 이미 큐에서
     * FIFO 선발된 워크플로우라 게이트를 다시 태우면 재park 되어 score 가 뒤로 밀린다
     * (ADR-025 양보 불가). 소스 이미지 검증·상태 전이·Tripo·세마포어 등 나머지 흐름은 동일하다.</p>
     *
     * @param workflowId          {@code model_generation_workflow} 행 ID
     * @param dispatchEpochMillis 드레이너가 재발행한 시각(epoch millis).
     *                            in-flight latency 측정용 (ADR-025 NN7).
     */
    void prepareFromAdmissionQueue(Long workflowId, long dispatchEpochMillis);
}
