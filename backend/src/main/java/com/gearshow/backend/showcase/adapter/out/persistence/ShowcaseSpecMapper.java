package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import org.springframework.stereotype.Component;

/**
 * ShowcaseSpec 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ShowcaseSpecMapper {

    public ShowcaseSpecJpaEntity toJpaEntity(ShowcaseSpec spec) {
        return ShowcaseSpecJpaEntity.builder()
                .id(spec.getId())
                .showcaseId(spec.getShowcaseId())
                .specType(spec.getSpecType())
                .specData(spec.getSpecData())
                .createdAt(spec.getCreatedAt())
                .updatedAt(spec.getUpdatedAt())
                .build();
    }

    public ShowcaseSpec toDomain(ShowcaseSpecJpaEntity entity) {
        return ShowcaseSpec.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .specType(entity.getSpecType())
                .specData(entity.getSpecData())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
