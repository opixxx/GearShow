package com.gearshow.backend.catalog.application.dto;

import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.Category;

import java.time.Instant;

/**
 * 카탈로그 아이템 목록 조회 결과 항목.
 */
public record CatalogItemListResult(
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String officialImageUrl,
        Instant createdAt
) {

    public static CatalogItemListResult from(CatalogItem item) {
        return new CatalogItemListResult(
                item.getId(),
                item.getCategory(),
                item.getBrand(),
                item.getModelCode(),
                item.getOfficialImageUrl(),
                item.getCreatedAt()
        );
    }
}
