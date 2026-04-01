package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import org.springframework.stereotype.Component;

/**
 * ShowcaseComment 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼.
 */
@Component
public class ShowcaseCommentMapper {

    public ShowcaseCommentJpaEntity toJpaEntity(ShowcaseComment comment) {
        return ShowcaseCommentJpaEntity.builder()
                .id(comment.getId())
                .showcaseId(comment.getShowcaseId())
                .authorId(comment.getAuthorId())
                .content(comment.getContent())
                .status(comment.getStatus())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    public ShowcaseComment toDomain(ShowcaseCommentJpaEntity entity) {
        return ShowcaseComment.builder()
                .id(entity.getId())
                .showcaseId(entity.getShowcaseId())
                .authorId(entity.getAuthorId())
                .content(entity.getContent())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
