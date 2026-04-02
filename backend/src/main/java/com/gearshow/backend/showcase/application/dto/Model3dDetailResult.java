package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;

import java.time.Instant;

/**
 * 3D 모델 상세 조회 결과.
 */
public record Model3dDetailResult(
        Long showcase3dModelId,
        String modelFileUrl,
        String previewImageUrl,
        ModelStatus modelStatus,
        String generationProvider,
        int sourceImageCount,
        Instant requestedAt,
        Instant generatedAt,
        String failureReason
) {

    public static Model3dDetailResult of(Showcase3dModel model, int sourceImageCount) {
        return new Model3dDetailResult(
                model.getId(),
                model.getModelFileUrl(),
                model.getPreviewImageUrl(),
                model.getModelStatus(),
                model.getGenerationProvider(),
                sourceImageCount,
                model.getRequestedAt(),
                model.getGeneratedAt(),
                model.getFailureReason());
    }
}
