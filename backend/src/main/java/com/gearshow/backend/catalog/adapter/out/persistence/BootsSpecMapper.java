package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.model.BootsSpec;
import org.springframework.stereotype.Component;

/**
 * BootsSpec 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class BootsSpecMapper {

    public BootsSpecJpaEntity toJpaEntity(BootsSpec bootsSpec) {
        return BootsSpecJpaEntity.builder()
                .id(bootsSpec.getId())
                .catalogItemId(bootsSpec.getCatalogItemId())
                .studType(bootsSpec.getStudType())
                .siloName(bootsSpec.getSiloName())
                .releaseYear(bootsSpec.getReleaseYear())
                .surfaceType(bootsSpec.getSurfaceType())
                .extraSpecJson(bootsSpec.getExtraSpecJson())
                .createdAt(bootsSpec.getCreatedAt())
                .updatedAt(bootsSpec.getUpdatedAt())
                .build();
    }

    public BootsSpec toDomain(BootsSpecJpaEntity entity) {
        return BootsSpec.builder()
                .id(entity.getId())
                .catalogItemId(entity.getCatalogItemId())
                .studType(entity.getStudType())
                .siloName(entity.getSiloName())
                .releaseYear(entity.getReleaseYear())
                .surfaceType(entity.getSurfaceType())
                .extraSpecJson(entity.getExtraSpecJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
