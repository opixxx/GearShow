package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemResult;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.out.BootsSpecPort;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.application.port.out.UniformSpecPort;
import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.policy.ModelCodeDuplicatePolicy;
import com.gearshow.backend.catalog.domain.vo.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카탈로그 아이템 등록 유스케이스 구현체.
 */
@Service
@RequiredArgsConstructor
public class CreateCatalogItemService implements CreateCatalogItemUseCase {

    private final CatalogItemPort catalogItemPort;
    private final BootsSpecPort bootsSpecPort;
    private final UniformSpecPort uniformSpecPort;

    @Override
    @Transactional
    public CreateCatalogItemResult create(CreateCatalogItemCommand command) {
        ModelCodeDuplicatePolicy.validate(
            command.category(),
            command.modelCode(),
            null,
            catalogItemPort::existsByCategoryAndModelCode
        );

        CatalogItem catalogItem = CatalogItem.create(
            command.category(),
            command.brand(),
            command.modelCode(),
            command.officialImageUrl()
        );

        CatalogItem item = catalogItemPort.save(catalogItem);

        saveSpec(item.getId(), command);

        return new CreateCatalogItemResult(item.getId());
    }

    /**
     * 카테고리에 따라 하위 스펙을 저장한다.
     */
    private void saveSpec(Long catalogItemId, CreateCatalogItemCommand command) {
        if (command.category() == Category.BOOTS && command.bootsSpec() != null) {
            var spec = command.bootsSpec();
            BootsSpec bootsSpec = BootsSpec.create(
                catalogItemId,
                spec.studType(),
                spec.siloName(),
                spec.releaseYear(),
                spec.surfaceType(),
                spec.extraSpecJson()
            );
            bootsSpecPort.save(bootsSpec);
        } else if (command.category() == Category.UNIFORM && command.uniformSpec() != null) {
            var spec = command.uniformSpec();
            UniformSpec uniformSpec = UniformSpec.create(
                catalogItemId,
                spec.clubName(),
                spec.season(),
                spec.league(),
                spec.kitType(),
                spec.extraSpecJson()
            );
            uniformSpecPort.save(uniformSpec);
        }
    }
}
