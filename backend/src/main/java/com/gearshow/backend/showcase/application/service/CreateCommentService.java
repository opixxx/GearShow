package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.CreateCommentUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.ShowcaseComment;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 작성 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class CreateCommentService implements CreateCommentUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseCommentPort showcaseCommentPort;

    @Override
    @Transactional
    public Long create(Long showcaseId, Long authorId, String content) {
        validateShowcaseActive(showcaseId);
        ShowcaseComment comment = ShowcaseComment.create(showcaseId, authorId, content);
        ShowcaseComment saved = showcaseCommentPort.save(comment);
        return saved.getId();
    }

    /**
     * ACTIVE 상태의 쇼케이스에만 댓글 작성이 가능하다.
     */
    private void validateShowcaseActive(Long showcaseId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        if (showcase.getStatus() != ShowcaseStatus.ACTIVE) {
            throw new NotFoundShowcaseException();
        }
    }
}
