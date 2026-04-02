package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.model.ShowcaseComment;

import java.time.Instant;

/**
 * 댓글 조회 결과.
 */
public record CommentResult(
        Long showcaseCommentId,
        Long authorId,
        String content,
        Instant createdAt
) {

    public static CommentResult from(ShowcaseComment comment) {
        return new CommentResult(
                comment.getId(), comment.getAuthorId(),
                comment.getContent(), comment.getCreatedAt());
    }
}
