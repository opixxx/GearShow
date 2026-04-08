package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.application.dto.UpdateCatalogItemCommand;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.UpdateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.exception.NotFoundCatalogItemException;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.policy.ModelCodeDuplicatePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 아이템 수정 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class UpdateCatalogItemService implements UpdateCatalogItemUseCase {

    private final CatalogItemPort catalogItemPort;
    private final GetCatalogItemUseCase getCatalogItemUseCase;

    @Override
    @Transactional
    public CatalogItemDetailResult update(Long catalogItemId, UpdateCatalogItemCommand command) {
        CatalogItem item = catalogItemPort.findById(catalogItemId)
                .orElseThrow(NotFoundCatalogItemException::new);

        ModelCodeDuplicatePolicy.validate(
            item.getCategory(),
            command.modelCode(),
            item.getModelCode(),
            catalogItemPort::existsByCategoryAndModelCode
        );

        CatalogItem updated = item.update(
            command.brand(),
            command.modelCode(),
            command.officialImageUrl()
        );
        catalogItemPort.save(updated);

        return getCatalogItemUseCase.getCatalogItem(catalogItemId);
    }
}
