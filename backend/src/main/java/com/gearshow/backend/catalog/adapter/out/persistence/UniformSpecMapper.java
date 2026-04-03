package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.model.UniformSpec;
import org.springframework.stereotype.Component;

/**
 * UniformSpec 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class UniformSpecMapper {

    public UniformSpecJpaEntity toJpaEntity(UniformSpec uniformSpec) {
        return UniformSpecJpaEntity.builder()
                .id(uniformSpec.getId())
                .catalogItemId(uniformSpec.getCatalogItemId())
                .clubName(uniformSpec.getClubName())
                .season(uniformSpec.getSeason())
                .league(uniformSpec.getLeague())

                .extraSpecJson(uniformSpec.getExtraSpecJson())
                .createdAt(uniformSpec.getCreatedAt())
                .updatedAt(uniformSpec.getUpdatedAt())
                .build();
    }

    public UniformSpec toDomain(UniformSpecJpaEntity entity) {
        return UniformSpec.builder()
                .id(entity.getId())
                .catalogItemId(entity.getCatalogItemId())
                .clubName(entity.getClubName())
                .season(entity.getSeason())
                .league(entity.getLeague())

                .extraSpecJson(entity.getExtraSpecJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
