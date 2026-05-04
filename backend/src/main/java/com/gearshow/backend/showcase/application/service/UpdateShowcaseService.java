package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.CatalogSearchSource;
import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.in.UpdateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.LoadCatalogForSearchPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ShowcaseUpdate;
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
    private final LoadCatalogForSearchPort loadCatalogForSearchPort;

    @Override
    @Transactional
    public void update(Long showcaseId, Long ownerId, UpdateShowcaseCommand command) {
        Showcase showcase = showcasePort.findById(showcaseId)
                .orElseThrow(NotFoundShowcaseException::new);
        showcase.validateOwner(ownerId);

        ShowcaseUpdate update = new ShowcaseUpdate(
                command.title(),
                command.description(),
                command.modelCode(),
                command.userSize(),
                command.conditionGrade(),
                command.wearCount(),
                command.isForSale()
        );
        Showcase updated = showcase.update(update);

        // ADR-018: title/description/modelCode 변경 시 search_text 재합성.
        // catalogItemId 는 update 흐름에서 변경되지 않으므로 동일 source 사용.
        Showcase withSearchText = updated.changeSearchText(composeSearchText(updated));
        showcasePort.save(withSearchText);
    }

    private String composeSearchText(Showcase showcase) {
        CatalogSearchSource source = showcase.getCatalogItemId() != null
                ? loadCatalogForSearchPort.findCatalogSearchSource(showcase.getCatalogItemId()).orElse(null)
                : null;
        return SearchTextComposer.compose(showcase, source);
    }
}
