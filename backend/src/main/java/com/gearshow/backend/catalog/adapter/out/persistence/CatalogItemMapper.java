package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.model.CatalogItem;
import org.springframework.stereotype.Component;

/**
 * CatalogItem 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class CatalogItemMapper {

    public CatalogItemJpaEntity toJpaEntity(CatalogItem catalogItem) {
        return CatalogItemJpaEntity.builder()
                .id(catalogItem.getId())
                .category(catalogItem.getCategory())
                .brand(catalogItem.getBrand())
                .modelCode(catalogItem.getModelCode())
                .officialImageUrl(catalogItem.getOfficialImageUrl())
                .status(catalogItem.getStatus())
                .createdAt(catalogItem.getCreatedAt())
                .updatedAt(catalogItem.getUpdatedAt())
                .build();
    }

    public CatalogItem toDomain(CatalogItemJpaEntity entity) {
        return CatalogItem.builder()
                .id(entity.getId())
                .category(entity.getCategory())
                .brand(entity.getBrand())
                .modelCode(entity.getModelCode())
                .officialImageUrl(entity.getOfficialImageUrl())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
