package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

import java.time.Instant;

/**
 * 쇼케이스 목록 조회 결과.
 */
public record ShowcaseListResult(
        Long showcaseId,
        String title,
        Category category,
        String brand,
        ConditionGrade conditionGrade,
        boolean isForSale,
        int wearCount,
        String primaryImageUrl,
        int commentCount,
        boolean has3dModel,
        Instant createdAt
) {}
