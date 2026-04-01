package com.gearshow.backend.catalog.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;

/**
 * 카탈로그 아이템 등록 커맨드.
 */
public record CreateCatalogItemCommand(
        Category category,
        String brand,
        String itemName,
        String modelCode,
        String officialImageUrl,
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
            String manufacturer,
            String extraSpecJson
    ) {}
}
