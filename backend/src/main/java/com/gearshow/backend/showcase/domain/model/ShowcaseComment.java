package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseCommentException;
import com.gearshow.backend.showcase.domain.vo.CommentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 쇼케이스 댓글 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 쇼케이스 삭제 시 함께 삭제된다.</p>
 */
@Getter
public class ShowcaseComment {

    private final Long id;
    private final Long showcaseId;
    private final Long authorId;
    private final String content;
    private final CommentStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private ShowcaseComment(Long id, Long showcaseId, Long authorId,
                            String content, CommentStatus status,
                            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.authorId = authorId;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 댓글을 생성한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param authorId   작성자 ID
     * @param content    댓글 내용
     * @return 생성된 댓글
     */
    public static ShowcaseComment create(Long showcaseId, Long authorId, String content) {
        validate(showcaseId, authorId, content);

        Instant now = Instant.now();
        return ShowcaseComment.builder()
                .showcaseId(showcaseId)
                .authorId(authorId)
                .content(content)
                .status(CommentStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 댓글 내용을 수정한다.
     *
     * @param content 수정할 내용
     * @return 수정된 댓글
     */
    public ShowcaseComment edit(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidShowcaseCommentException();
        }
        return ShowcaseComment.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .authorId(this.authorId)
                .content(content)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * 댓글을 삭제한다 (소프트 삭제).
     *
     * @return 삭제된 댓글
     */
    public ShowcaseComment delete() {
        return ShowcaseComment.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .authorId(this.authorId)
                .content(this.content)
                .status(CommentStatus.DELETED)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    private static void validate(Long showcaseId, Long authorId, String content) {
        if (showcaseId == null || authorId == null
                || content == null || content.isBlank()) {
            throw new InvalidShowcaseCommentException();
        }
    }
}
