package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

import java.time.Instant;

/**
 * 쇼케이스 목록 조회 결과.
 */
public record ShowcaseListResult(
        Long showcaseId,
        String title,
        ConditionGrade conditionGrade,
        boolean isForSale,
        int wearCount,
        String primaryImageUrl,
        int commentCount,
        boolean has3dModel,
        Instant createdAt
) {}
