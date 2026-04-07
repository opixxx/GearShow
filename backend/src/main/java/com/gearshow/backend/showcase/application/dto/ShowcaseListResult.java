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
        String userSize,
        ConditionGrade conditionGrade,
        boolean isForSale,
        int wearCount,
        String primaryImageUrl,
        int commentCount,
        boolean has3dModel,
        SpecSummary spec,
        Instant createdAt
) {

    /**
     * 카테고리별 스펙 요약 정보.
     * 새 카테고리 추가 시 구현체만 추가하면 된다.
     */
    public sealed interface SpecSummary permits BootsSpecSummary, UniformSpecSummary {}

    /**
     * 축구화 스펙 요약.
     */
    public record BootsSpecSummary(
            String studType,
            String siloName,
            String surfaceType
    ) implements SpecSummary {}

    /**
     * 유니폼 스펙 요약.
     */
    public record UniformSpecSummary(
            String clubName,
            String season,
            String league,
            String kitType
    ) implements SpecSummary {}
}
