package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.Showcase;
import org.springframework.stereotype.Component;

/**
 * Showcase 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ShowcaseMapper {

    public ShowcaseJpaEntity toJpaEntity(Showcase showcase) {
        return ShowcaseJpaEntity.builder()
                .id(showcase.getId())
                .ownerId(showcase.getOwnerId())
                .catalogItemId(showcase.getCatalogItemId())
                .title(showcase.getTitle())
                .description(showcase.getDescription())
                .userSize(showcase.getUserSize())
                .conditionGrade(showcase.getConditionGrade())
                .wearCount(showcase.getWearCount())
                .forSale(showcase.isForSale())
                .status(showcase.getStatus())
                .createdAt(showcase.getCreatedAt())
                .updatedAt(showcase.getUpdatedAt())
                .build();
    }

    public Showcase toDomain(ShowcaseJpaEntity entity) {
        return Showcase.builder()
                .id(entity.getId())
                .ownerId(entity.getOwnerId())
                .catalogItemId(entity.getCatalogItemId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .userSize(entity.getUserSize())
                .conditionGrade(entity.getConditionGrade())
                .wearCount(entity.getWearCount())
                .forSale(entity.isForSale())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
