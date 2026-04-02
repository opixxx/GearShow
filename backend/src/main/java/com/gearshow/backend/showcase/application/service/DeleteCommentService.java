package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.exception.NotAuthorCommentException;
import com.gearshow.backend.showcase.application.exception.NotFoundShowcaseCommentException;
import com.gearshow.backend.showcase.application.port.in.DeleteCommentUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 삭제 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class DeleteCommentService implements DeleteCommentUseCase {

    private final ShowcaseCommentPort showcaseCommentPort;

    @Override
    @Transactional
    public void delete(Long showcaseId, Long commentId, Long authorId) {
        ShowcaseComment comment = findComment(commentId);
        validateBelongsToShowcase(comment, showcaseId);
        validateAuthor(comment, authorId);

        ShowcaseComment deleted = comment.delete();
        showcaseCommentPort.save(deleted);
    }

    private ShowcaseComment findComment(Long commentId) {
        return showcaseCommentPort.findById(commentId)
                .orElseThrow(NotFoundShowcaseCommentException::new);
    }

    /**
     * 댓글이 해당 쇼케이스에 소속되어 있는지 검증한다.
     */
    private void validateBelongsToShowcase(ShowcaseComment comment, Long showcaseId) {
        if (!comment.getShowcaseId().equals(showcaseId)) {
            throw new NotFoundShowcaseCommentException();
        }
    }

    private void validateAuthor(ShowcaseComment comment, Long authorId) {
        if (!comment.getAuthorId().equals(authorId)) {
            throw new NotAuthorCommentException();
        }
    }
}
