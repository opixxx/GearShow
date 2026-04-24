package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import org.springframework.stereotype.Component;

/**
 * {@link Showcase3dModel} 도메인 ↔ JPA 엔티티 매퍼. 완성품 필드만 다룬다 (ADR-010).
 */
@Component
public class Showcase3dModelMapper {

    public Showcase3dModelJpaEntity toJpaEntity(Showcase3dModel model) {
        return Showcase3dModelJpaEntity.builder()
                .id(model.getId())
                .showcaseId(model.getShowcaseId())
                .modelFileUrl(model.getModelFileUrl())
                .previewImageUrl(model.getPreviewImageUrl())
                .generatedAt(model.getGeneratedAt())
                .createdAt(model.getCreatedAt())
                .updatedAt(model.getUpdatedAt())
                .build();
    }

    public Showcase3dModel toDomain(Showcase3dModelJpaEntity entity) {
        return Showcase3dModel.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .modelFileUrl(entity.getModelFileUrl())
                .previewImageUrl(entity.getPreviewImageUrl())
                .generatedAt(entity.getGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
