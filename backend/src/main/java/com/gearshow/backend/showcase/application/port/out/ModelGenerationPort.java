package com.gearshow.backend.showcase.application.port.out;

/**
 * 3D 모델 생성 요청 Outbound Port.
 * 비동기 메시징 시스템(Kafka)을 통해 3D 모델 생성을 요청한다.
 */
public interface ModelGenerationPort {

    /**
     * 3D 모델 생성을 비동기로 요청한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @param showcaseId        쇼케이스 ID
     */
    void requestGeneration(Long showcase3dModelId, Long showcaseId);
}
