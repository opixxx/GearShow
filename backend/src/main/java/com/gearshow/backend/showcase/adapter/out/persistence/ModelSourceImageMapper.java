package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import org.springframework.stereotype.Component;

/**
 * ModelSourceImage 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ModelSourceImageMapper {

    public ModelSourceImageJpaEntity toJpaEntity(ModelSourceImage image) {
        return ModelSourceImageJpaEntity.builder()
                .id(image.getId())
                .showcase3dModelId(image.getShowcase3dModelId())
                .imageUrl(image.getImageUrl())
                .angleType(image.getAngleType())
                .sortOrder(image.getSortOrder())
                .createdAt(image.getCreatedAt())
                .build();
    }

    public ModelSourceImage toDomain(ModelSourceImageJpaEntity entity) {
        return ModelSourceImage.builder()
                .id(entity.getId())
                .showcase3dModelId(entity.getShowcase3dModelId())
                .imageUrl(entity.getImageUrl())
                .angleType(entity.getAngleType())
                .sortOrder(entity.getSortOrder())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
