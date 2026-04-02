package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.exception.NotOwnerShowcaseException;
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
        Showcase showcase = findShowcase(showcaseId);
        validateOwner(showcase, ownerId);

        Showcase updated = applyUpdate(showcase, command);
        showcasePort.save(updated);
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

    /**
     * null이 아닌 필드만 업데이트한다 (Partial Update).
     */
    private Showcase applyUpdate(Showcase showcase, UpdateShowcaseCommand command) {
        return Showcase.builder()
                .id(showcase.getId())
                .ownerId(showcase.getOwnerId())
                .catalogItemId(showcase.getCatalogItemId())
                .title(command.title() != null ? command.title() : showcase.getTitle())
                .description(command.description() != null ? command.description() : showcase.getDescription())
                .userSize(command.userSize() != null ? command.userSize() : showcase.getUserSize())
                .conditionGrade(command.conditionGrade() != null ? command.conditionGrade() : showcase.getConditionGrade())
                .wearCount(command.wearCount() != null ? command.wearCount() : showcase.getWearCount())
                .forSale(command.isForSale() != null ? command.isForSale() : showcase.isForSale())
                .status(showcase.getStatus())
                .createdAt(showcase.getCreatedAt())
                .updatedAt(java.time.Instant.now())
                .build();
    }
}
