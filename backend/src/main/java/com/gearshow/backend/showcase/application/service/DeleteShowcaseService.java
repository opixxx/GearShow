package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
import com.gearshow.backend.showcase.application.port.in.DeleteShowcaseUseCase;
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

    @Override
    @Transactional
    public void delete(Long showcaseId, Long ownerId) {
        Showcase showcase = findShowcase(showcaseId);
        validateOwner(showcase, ownerId);

        Showcase deleted = showcase.delete();
        showcasePort.save(deleted);
    }

    private Showcase findShowcase(Long showcaseId) {
        return showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
    }

    private void validateOwner(Showcase showcase, Long ownerId) {
        if (!showcase.getOwnerId().equals(ownerId)) {
            throw new NotOwnerShowcaseException();
        }
    }
}
