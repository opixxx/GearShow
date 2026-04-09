package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.out.BootsSpecPort;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.application.port.out.UniformSpecPort;
import com.gearshow.backend.catalog.domain.exception.NotFoundCatalogItemException;
import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.vo.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 아이템 상세 조회 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class GetCatalogItemService implements GetCatalogItemUseCase {

    private final CatalogItemPort catalogItemPort;
    private final BootsSpecPort bootsSpecPort;
    private final UniformSpecPort uniformSpecPort;

    @Override
    @Transactional(readOnly = true)
    public CatalogItemDetailResult getCatalogItem(Long catalogItemId) {
        CatalogItem item = catalogItemPort.findById(catalogItemId)
                .orElseThrow(NotFoundCatalogItemException::new);

        BootsSpec bootsSpec = findBootsSpec(item.getCategory(), catalogItemId);
        UniformSpec uniformSpec = findUniformSpec(item.getCategory(), catalogItemId);

        return CatalogItemDetailResult.of(item, bootsSpec, uniformSpec);
    }

    private BootsSpec findBootsSpec(Category category, Long catalogItemId) {
        if (category != Category.BOOTS) {
            return null;
        }
        return bootsSpecPort.findByCatalogItemId(catalogItemId).orElse(null);
    }

    private UniformSpec findUniformSpec(Category category, Long catalogItemId) {
        if (category != Category.UNIFORM) {
            return null;
        }
        return uniformSpecPort.findByCatalogItemId(catalogItemId).orElse(null);
    }
}
