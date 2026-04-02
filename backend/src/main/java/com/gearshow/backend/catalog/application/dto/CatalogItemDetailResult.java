package com.gearshow.backend.catalog.application.dto;

import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;

import java.time.Instant;

/**
 * 카탈로그 아이템 상세 조회 결과.
 */
public record CatalogItemDetailResult(
        Long catalogItemId,
        Category category,
        String brand,
        String itemName,
        String modelCode,
        String officialImageUrl,
        CatalogStatus catalogStatus,
        BootsSpecResult bootsSpec,
        UniformSpecResult uniformSpec,
        Instant createdAt
) {

    public record BootsSpecResult(
            StudType studType,
            String siloName,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {
        public static BootsSpecResult from(BootsSpec spec) {
            return new BootsSpecResult(
                    spec.getStudType(), spec.getSiloName(),
                    spec.getReleaseYear(), spec.getSurfaceType(),
                    spec.getExtraSpecJson());
        }
    }

    public record UniformSpecResult(
            String clubName,
            String season,
            String league,
            String manufacturer,
            String extraSpecJson
    ) {
        public static UniformSpecResult from(UniformSpec spec) {
            return new UniformSpecResult(
                    spec.getClubName(), spec.getSeason(),
                    spec.getLeague(), spec.getManufacturer(),
                    spec.getExtraSpecJson());
        }
    }

    public static CatalogItemDetailResult of(CatalogItem item, BootsSpec bootsSpec, UniformSpec uniformSpec) {
        return new CatalogItemDetailResult(
                item.getId(), item.getCategory(), item.getBrand(),
                item.getItemName(), item.getModelCode(), item.getOfficialImageUrl(),
                item.getStatus(),
                bootsSpec != null ? BootsSpecResult.from(bootsSpec) : null,
                uniformSpec != null ? UniformSpecResult.from(uniformSpec) : null,
                item.getCreatedAt());
    }
}
