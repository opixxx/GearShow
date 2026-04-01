package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import org.springframework.stereotype.Component;

/**
 * ShowcaseImage 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ShowcaseImageMapper {

    public ShowcaseImageJpaEntity toJpaEntity(ShowcaseImage image) {
        return ShowcaseImageJpaEntity.builder()
                .id(image.getId())
                .showcaseId(image.getShowcaseId())
                .imageUrl(image.getImageUrl())
                .sortOrder(image.getSortOrder())
                .primary(image.isPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }

    public ShowcaseImage toDomain(ShowcaseImageJpaEntity entity) {
        return ShowcaseImage.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .imageUrl(entity.getImageUrl())
                .sortOrder(entity.getSortOrder())
                .primary(entity.isPrimary())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
