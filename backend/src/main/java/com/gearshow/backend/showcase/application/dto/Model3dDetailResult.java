package com.gearshow.backend.showcase.application.dto;

import java.time.Instant;

/**
 * 3D 모델 상세 조회 결과 (ADR-010 프로세스 분리 반영).
 *
 * <p>응답용 {@code modelStatus} 는 {@code model_generation_workflow.current_step} + 완성품 존재
 * 여부로부터 유도된 {@link ShowcaseModelStatus} 이다. Raw 엔티티 컬럼이 아니다.</p>
 */
public record Model3dDetailResult(
        Long showcase3dModelId,
        String modelFileUrl,
        String previewImageUrl,
        ShowcaseModelStatus modelStatus,
        int sourceImageCount,
        Instant requestedAt,
        Instant generatedAt,
        String failureMessage
) {
}
