package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.port.in.DeleteShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcaseCommentPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쇼케이스 삭제 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class DeleteShowcaseService implements DeleteShowcaseUseCase {

    private final ShowcasePort showcasePort;
    private final ShowcaseCommentPort showcaseCommentPort;

    @Override
    @Transactional
    public void delete(Long showcaseId, Long ownerId) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        showcase.validateOwner(ownerId);

        Showcase deleted = showcase.delete();
        showcasePort.save(deleted);

        showcaseCommentPort.softDeleteAllByShowcaseId(showcaseId);
    }
}
