package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.exception.NotAuthorCommentException;
import com.gearshow.backend.showcase.application.exception.NotFoundShowcaseCommentException;
import com.gearshow.backend.showcase.application.port.in.UpdateCommentUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 댓글 수정 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class UpdateCommentService implements UpdateCommentUseCase {

    private final ShowcaseCommentPort showcaseCommentPort;

    @Override
    @Transactional
    public void update(Long commentId, Long authorId, String content) {
        ShowcaseComment comment = findComment(commentId);
        validateAuthor(comment, authorId);

        ShowcaseComment updated = ShowcaseComment.builder()
                .id(comment.getId())
                .showcaseId(comment.getShowcaseId())
                .authorId(comment.getAuthorId())
                .content(content)
                .status(comment.getStatus())
                .createdAt(comment.getCreatedAt())
                .updatedAt(Instant.now())
                .build();

        showcaseCommentPort.save(updated);
    }

    private ShowcaseComment findComment(Long commentId) {
        return showcaseCommentPort.findById(commentId)
                .orElseThrow(NotFoundShowcaseCommentException::new);
    }

    private void validateAuthor(ShowcaseComment comment, Long authorId) {
        if (!comment.getAuthorId().equals(authorId)) {
            throw new NotAuthorCommentException();
        }
    }
}
