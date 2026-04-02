package com.gearshow.backend.showcase.application.port.in;

import com.gearshow.backend.showcase.application.dto.Model3dDetailResult;

/**
 * 3D 모델 상태 조회 유스케이스.
 */
public interface GetModel3dUseCase {

    /**
     * 쇼케이스의 3D 모델 상태를 조회한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @return 3D 모델 상세 정보
     */
    Model3dDetailResult getModel3d(Long showcaseId);
}
