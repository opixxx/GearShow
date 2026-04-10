package com.gearshow.backend.showcase.application.port.in;

/**
 * 3D 모델 생성 처리 유스케이스.
 *
 * <p>외부 트리거(Kafka Consumer 등)로부터 호출되어 다음 흐름을 수행한다:
 * 1. 모델 조회 + GENERATING 상태로 전환
 * 2. 외부 모델 생성 클라이언트 호출
 * 3. 결과에 따라 COMPLETED/FAILED 상태 저장 + has3dModel 동기화
 * </p>
 */
public interface ProcessModelGenerationUseCase {

    /**
     * 3D 모델 생성을 처리한다.
     *
     * @param showcase3dModelId 처리할 3D 모델 ID
     * @param showcaseId        대상 쇼케이스 ID
     */
    void process(Long showcase3dModelId, Long showcaseId);
}
