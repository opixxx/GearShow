package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.DeleteCommentUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseCommentException;
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
        ShowcaseComment comment = showcaseCommentPort.findById(commentId)
                .orElseThrow(NotFoundShowcaseCommentException::new);
        comment.validateBelongsTo(showcaseId);
        comment.validateAuthor(authorId);

        ShowcaseComment deleted = comment.delete();
        showcaseCommentPort.save(deleted);
    }
}
