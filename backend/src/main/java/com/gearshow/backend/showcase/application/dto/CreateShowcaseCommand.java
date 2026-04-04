package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

/**
 * 쇼케이스 등록 커맨드.
 *
 * @param catalogItemId 카탈로그 아이템 ID (선택, null 허용)
 * @param category      카테고리 (필수)
 * @param brand         브랜드명 (필수)
 * @param modelCode     모델 코드 (선택)
 */
public record CreateShowcaseCommand(
        Long ownerId,
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String title,
        String description,
        String userSize,
        ConditionGrade conditionGrade,
        int wearCount,
        boolean isForSale,
        int primaryImageIndex,
        boolean hasModelSourceImages,
        BootsSpecCommand bootsSpec,
        UniformSpecCommand uniformSpec
) {

    /**
     * 축구화 스펙 커맨드.
     */
    public record BootsSpecCommand(
            StudType studType,
            String siloName,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    /**
     * 유니폼 스펙 커맨드.
     */
    public record UniformSpecCommand(
            String clubName,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {}
}
