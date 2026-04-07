package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;

/**
 * 쇼케이스 등록 응답 DTO.
 *
 * @param showcaseId   등록된 쇼케이스 ID
 * @param model3dStatus 3D 모델 생성 상태 (3D 모델 소스 이미지가 없으면 null)
 */
public record CreateShowcaseResponse(
        Long showcaseId,
        String model3dStatus
) {

    public static CreateShowcaseResponse from(CreateShowcaseResult result) {
        return new CreateShowcaseResponse(
                result.showcaseId(),
                result.model3dStatus() != null ? result.model3dStatus().name() : null);
    }
}
