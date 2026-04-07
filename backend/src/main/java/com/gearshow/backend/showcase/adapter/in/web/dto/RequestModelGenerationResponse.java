package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;

/**
 * 3D 모델 생성 요청 응답 DTO.
 *
 * @param showcase3dModelId 3D 모델 ID
 * @param modelStatus       현재 모델 생성 상태
 */
public record RequestModelGenerationResponse(
        Long showcase3dModelId,
        String modelStatus
) {

    public static RequestModelGenerationResponse from(ModelGenerationResult result) {
        return new RequestModelGenerationResponse(
                result.showcase3dModelId(),
                result.modelStatus().name());
    }
}
