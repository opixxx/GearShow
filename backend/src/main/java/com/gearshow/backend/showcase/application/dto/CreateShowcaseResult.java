package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.vo.ModelStatus;

/**
 * 쇼케이스 등록 결과.
 */
public record CreateShowcaseResult(
        Long showcaseId,
        ModelStatus model3dStatus
) {}
