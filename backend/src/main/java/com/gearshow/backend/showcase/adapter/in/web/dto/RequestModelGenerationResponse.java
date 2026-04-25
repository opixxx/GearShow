package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;

/**
 * 3D 모델 생성 재요청 응답 DTO.
 *
 * @param workflowId  생성된 {@code model_generation_workflow.id}
 * @param modelStatus 응답용 파생 상태. 신규/재시도 직후엔 {@link ShowcaseModelStatus#GENERATING}.
 */
public record RequestModelGenerationResponse(
        Long workflowId,
        ShowcaseModelStatus modelStatus
) {
    public static RequestModelGenerationResponse from(ModelGenerationResult result) {
        return new RequestModelGenerationResponse(result.workflowId(), result.modelStatus());
    }
}
