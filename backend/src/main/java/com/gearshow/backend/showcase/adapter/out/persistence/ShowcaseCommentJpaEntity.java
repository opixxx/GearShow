package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.CommentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쇼케이스 댓글 JPA 엔티티.
 */
@Entity
@Table(name = "showcase_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseCommentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_comment_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false)
    private Long showcaseId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_status", nullable = false)
    private CommentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ShowcaseCommentJpaEntity(Long id, Long showcaseId, Long authorId,
                                     String content, CommentStatus status,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.authorId = authorId;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
