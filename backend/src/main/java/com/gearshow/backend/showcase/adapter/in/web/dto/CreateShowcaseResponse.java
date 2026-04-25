package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;

/**
 * 쇼케이스 등록 응답 DTO.
 *
 * <p>3D 모델 소스 이미지 없이 등록되거나 {@code content_hash} dedup 히트 시
 * {@code model3dStatus} 는 {@code null} 일 수 있다.</p>
 *
 * @param showcaseId    생성된 쇼케이스 ID
 * @param model3dStatus 3D 모델 상태 (nullable)
 */
public record CreateShowcaseResponse(
        Long showcaseId,
        ShowcaseModelStatus model3dStatus
) {
    public static CreateShowcaseResponse from(CreateShowcaseResult result) {
        return new CreateShowcaseResponse(result.showcaseId(), result.model3dStatus());
    }
}
