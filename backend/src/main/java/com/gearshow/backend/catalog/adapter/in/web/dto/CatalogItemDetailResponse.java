package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;

import java.time.Instant;

/**
 * 카탈로그 아이템 상세 조회 응답 DTO.
 */
public record CatalogItemDetailResponse(
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String officialImageUrl,
        CatalogStatus catalogStatus,
        BootsSpecResponse bootsSpec,
        UniformSpecResponse uniformSpec,
        Instant createdAt
) {

    public record BootsSpecResponse(
            StudType studType,
            String siloName,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    public record UniformSpecResponse(
            String clubName,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {}

    public static CatalogItemDetailResponse from(CatalogItemDetailResult result) {
        return new CatalogItemDetailResponse(
                result.catalogItemId(), result.category(), result.brand(),
                result.modelCode(), result.officialImageUrl(),
                result.catalogStatus(),
                result.bootsSpec() != null ? new BootsSpecResponse(
                        result.bootsSpec().studType(), result.bootsSpec().siloName(),
                        result.bootsSpec().releaseYear(), result.bootsSpec().surfaceType(),
                        result.bootsSpec().extraSpecJson()) : null,
                result.uniformSpec() != null ? new UniformSpecResponse(
                        result.uniformSpec().clubName(), result.uniformSpec().season(),
                        result.uniformSpec().league(), result.uniformSpec().kitType(),
                        result.uniformSpec().extraSpecJson()) : null,
                result.createdAt());
    }
}
