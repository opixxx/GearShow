package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.in.UpdateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쇼케이스 수정 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class UpdateShowcaseService implements UpdateShowcaseUseCase {

    private final ShowcasePort showcasePort;

    @Override
    @Transactional
    public void update(Long showcaseId, Long ownerId, UpdateShowcaseCommand command) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        showcase.validateOwner(ownerId);

        Showcase updated = showcase.update(
            command.title(),
            command.description(),
            command.modelCode(),
            command.userSize(),
            command.conditionGrade(),
            command.wearCount(),
            command.isForSale()
        );
        showcasePort.save(updated);
    }
}
