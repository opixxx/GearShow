package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.application.dto.CreateCatalogItemResult;
import com.gearshow.backend.catalog.application.port.in.CreateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.out.BootsSpecPort;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.application.port.out.UniformSpecPort;
import com.gearshow.backend.catalog.domain.exception.DuplicateModelCodeException;
import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
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
        validateModelCode(command.category(), command.modelCode());

        CatalogItem item = saveCatalogItem(command);
        saveSpec(item.getId(), item.getCategory(), command);

        return new CreateCatalogItemResult(item.getId());
    }

    private void validateModelCode(Category category, String modelCode) {
        if (modelCode != null && catalogItemPort.existsByCategoryAndModelCode(category, modelCode)) {
            throw new DuplicateModelCodeException();
        }
    }

    private CatalogItem saveCatalogItem(CreateCatalogItemCommand command) {
        CatalogItem item = CatalogItem.create(command.category(), command.brand(), command.itemName());
        // modelCode, officialImageUrl은 Builder로 직접 설정
        CatalogItem withDetails = CatalogItem.builder()
                .id(null)
                .category(command.category())
                .brand(command.brand())
                .itemName(command.itemName())
                .modelCode(command.modelCode())
                .officialImageUrl(command.officialImageUrl())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
        return catalogItemPort.save(withDetails);
    }

    /**
     * 카테고리에 따라 하위 스펙을 저장한다.
     */
    private void saveSpec(Long catalogItemId, Category category, CreateCatalogItemCommand command) {
        if (category == Category.BOOTS && command.bootsSpec() != null) {
            saveBootsSpec(catalogItemId, command.bootsSpec());
        } else if (category == Category.UNIFORM && command.uniformSpec() != null) {
            saveUniformSpec(catalogItemId, command.uniformSpec());
        }
    }

    private void saveBootsSpec(Long catalogItemId, CreateCatalogItemCommand.BootsSpecCommand spec) {
        BootsSpec bootsSpec = BootsSpec.builder()
                .catalogItemId(catalogItemId)
                .studType(spec.studType())
                .siloName(spec.siloName())
                .releaseYear(spec.releaseYear())
                .surfaceType(spec.surfaceType())
                .extraSpecJson(spec.extraSpecJson())
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
        bootsSpecPort.save(bootsSpec);
    }

    private void saveUniformSpec(Long catalogItemId, CreateCatalogItemCommand.UniformSpecCommand spec) {
        UniformSpec uniformSpec = UniformSpec.builder()
                .catalogItemId(catalogItemId)
                .clubName(spec.clubName())
                .season(spec.season())
                .league(spec.league())
                .extraSpecJson(spec.extraSpecJson())
                .createdAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();
        uniformSpecPort.save(uniformSpec);
    }
}
