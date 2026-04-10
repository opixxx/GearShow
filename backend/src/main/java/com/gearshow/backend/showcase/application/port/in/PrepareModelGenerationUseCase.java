package com.gearshow.backend.showcase.application.port.in;

/**
 * 3D 모델 생성 준비 유스케이스 (Inbound Port).
 *
 * <p>Worker 가 Kafka 메시지 수신 후 호출한다.
 * 이미지 업로드 + Tripo task 생성을 수행하고 즉시 반환하며,
 * 이후 Tripo 폴링은 별도 스케줄러가 담당한다.</p>
 *
 * <p>이 유스케이스는 <b>비즈니스 실패 시 예외를 던지지 않는다</b>.
 * 모델 상태를 FAILED 또는 UNAVAILABLE 로 직접 전환하고 정상 반환하여,
 * Kafka 재시도가 발생하지 않도록 한다 (재시도는 멱등성 가드에 막혀 무의미).</p>
 */
public interface PrepareModelGenerationUseCase {

    /**
     * 3D 모델 생성을 시작할 준비를 한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @param showcaseId        쇼케이스 ID
     */
    void prepare(Long showcase3dModelId, Long showcaseId);
}
