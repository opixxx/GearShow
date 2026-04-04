package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ShowcaseBootsSpec;
import org.springframework.stereotype.Component;

/**
 * ShowcaseBootsSpec 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ShowcaseBootsSpecMapper {

    public ShowcaseBootsSpecJpaEntity toJpaEntity(ShowcaseBootsSpec spec) {
        return ShowcaseBootsSpecJpaEntity.builder()
                .id(spec.getId())
                .showcaseId(spec.getShowcaseId())
                .studType(spec.getStudType())
                .siloName(spec.getSiloName())
                .releaseYear(spec.getReleaseYear())
                .surfaceType(spec.getSurfaceType())
                .extraSpecJson(spec.getExtraSpecJson())
                .createdAt(spec.getCreatedAt())
                .updatedAt(spec.getUpdatedAt())
                .build();
    }

    public ShowcaseBootsSpec toDomain(ShowcaseBootsSpecJpaEntity entity) {
        return ShowcaseBootsSpec.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
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
