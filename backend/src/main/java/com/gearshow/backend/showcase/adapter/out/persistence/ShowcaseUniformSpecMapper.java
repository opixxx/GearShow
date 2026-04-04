package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ShowcaseUniformSpec;
import org.springframework.stereotype.Component;

/**
 * ShowcaseUniformSpec 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ShowcaseUniformSpecMapper {

    public ShowcaseUniformSpecJpaEntity toJpaEntity(ShowcaseUniformSpec spec) {
        return ShowcaseUniformSpecJpaEntity.builder()
                .id(spec.getId())
                .showcaseId(spec.getShowcaseId())
                .clubName(spec.getClubName())
                .season(spec.getSeason())
                .league(spec.getLeague())
                .kitType(spec.getKitType())
                .extraSpecJson(spec.getExtraSpecJson())
                .createdAt(spec.getCreatedAt())
                .updatedAt(spec.getUpdatedAt())
                .build();
    }

    public ShowcaseUniformSpec toDomain(ShowcaseUniformSpecJpaEntity entity) {
        return ShowcaseUniformSpec.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .clubName(entity.getClubName())
                .season(entity.getSeason())
                .league(entity.getLeague())
                .kitType(entity.getKitType())
                .extraSpecJson(entity.getExtraSpecJson())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
