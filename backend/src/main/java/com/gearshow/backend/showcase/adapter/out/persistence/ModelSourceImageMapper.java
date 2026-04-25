package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import org.springframework.stereotype.Component;

/**
 * ModelSourceImage 도메인 ↔ JPA 엔티티 매퍼 (ADR-010 showcase_id 직접 참조).
 */
@Component
public class ModelSourceImageMapper {

    public ModelSourceImageJpaEntity toJpaEntity(ModelSourceImage image) {
        return ModelSourceImageJpaEntity.builder()
                .id(image.getId())
                .showcaseId(image.getShowcaseId())
                .imageUrl(image.getImageUrl())
                .angleType(image.getAngleType())
                .sortOrder(image.getSortOrder())
                .createdAt(image.getCreatedAt())
                .build();
    }

    public ModelSourceImage toDomain(ModelSourceImageJpaEntity entity) {
        return ModelSourceImage.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .imageUrl(entity.getImageUrl())
                .angleType(entity.getAngleType())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
