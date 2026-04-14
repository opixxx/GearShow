package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import org.springframework.stereotype.Component;

/**
 * Showcase3dModel 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class Showcase3dModelMapper {

    public Showcase3dModelJpaEntity toJpaEntity(Showcase3dModel model) {
        return Showcase3dModelJpaEntity.builder()
                .id(model.getId())
                .showcaseId(model.getShowcaseId())
                .modelFileUrl(model.getModelFileUrl())
                .previewImageUrl(model.getPreviewImageUrl())
                .modelStatus(model.getModelStatus())
                .generationProvider(model.getGenerationProvider())
                .generationTaskId(model.getGenerationTaskId())
                .requestedAt(model.getRequestedAt())
                .generatedAt(model.getGeneratedAt())
                .lastPolledAt(model.getLastPolledAt())
                .failureReason(model.getFailureReason())
                .createdAt(model.getCreatedAt())
                .retryCount(model.getRetryCount())
                .build();
    }

    public Showcase3dModel toDomain(Showcase3dModelJpaEntity entity) {
        return Showcase3dModel.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .modelFileUrl(entity.getModelFileUrl())
                .previewImageUrl(entity.getPreviewImageUrl())
                .modelStatus(entity.getModelStatus())
                .generationProvider(entity.getGenerationProvider())
                .generationTaskId(entity.getGenerationTaskId())
                .requestedAt(entity.getRequestedAt())
                .generatedAt(entity.getGeneratedAt())
                .lastPolledAt(entity.getLastPolledAt())
                .failureReason(entity.getFailureReason())
                .createdAt(entity.getCreatedAt())
                .retryCount(entity.getRetryCount())
                .build();
    }
}
