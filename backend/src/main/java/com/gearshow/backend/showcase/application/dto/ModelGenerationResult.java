package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.vo.ModelStatus;

/**
 * 3D 모델 생성 요청 결과.
 */
public record ModelGenerationResult(
        Long showcase3dModelId,
        ModelStatus modelStatus
) {}
