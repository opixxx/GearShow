package com.gearshow.backend.showcase.application.dto;

/**
 * 쇼케이스 등록 결과.
 */
public record CreateShowcaseResult(
        Long showcaseId,
        ShowcaseModelStatus model3dStatus
) {}
