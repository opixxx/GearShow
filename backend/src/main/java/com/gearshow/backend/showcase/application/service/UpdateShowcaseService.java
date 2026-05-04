package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.application.port.in.UpdateShowcaseUseCase;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.exception.NotFoundShowcaseException;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.vo.ShowcaseUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쇼케이스 수정 유스케이스 구현체.
 *
 * <p>ADR-018 §D4: 직접 입력 토큰 (title/description/modelCode) 중 하나라도 변경되면
 * search_text 를 재합성한다. 그 외 update (userSize/conditionGrade/wearCount/forSale) 는
 * search_text 와 무관하므로 catalog DB 재조회를 생략한다 — read-write 비대칭 최소화.</p>
 */
@Service
@RequiredArgsConstructor
public class UpdateShowcaseService implements UpdateShowcaseUseCase {

    private final ShowcasePort showcasePort;
    private final SearchTextSynchronizer searchTextSynchronizer;

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

        // ADR-018 §D4: 직접 입력 토큰 변경 시에만 search_text 재합성 (catalog DB 재조회 회피).
        // catalogItemId 는 update 흐름에서 변경 불가 (UpdateShowcaseCommand 에 미포함).
        Showcase finalState = isSearchTextAffected(command)
                ? searchTextSynchronizer.synchronize(updated)
                : updated;
        showcasePort.save(finalState);
    }

    private boolean isSearchTextAffected(UpdateShowcaseCommand command) {
        return command.title() != null
                || command.description() != null
                || command.modelCode() != null;
    }
}
