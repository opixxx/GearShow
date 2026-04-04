package com.gearshow.backend.catalog.application.service;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.application.dto.UpdateCatalogItemCommand;
import com.gearshow.backend.catalog.application.port.in.GetCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.in.UpdateCatalogItemUseCase;
import com.gearshow.backend.catalog.application.port.out.CatalogItemPort;
import com.gearshow.backend.catalog.domain.exception.DuplicateModelCodeException;
import com.gearshow.backend.catalog.domain.exception.NotFoundCatalogItemException;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
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

        validateModelCode(item, command.modelCode());

        CatalogItem updated = buildUpdatedItem(item, command);
        catalogItemPort.save(updated);

        return getCatalogItemUseCase.getCatalogItem(catalogItemId);
    }

    /**
     * 모델 코드 변경 시 중복 여부를 확인한다.
     * 기존 모델 코드와 동일하면 검사를 건너뛴다.
     */
    private void validateModelCode(CatalogItem item, String newModelCode) {
        if (newModelCode == null || newModelCode.equals(item.getModelCode())) {
            return;
        }
        if (catalogItemPort.existsByCategoryAndModelCode(item.getCategory(), newModelCode)) {
            throw new DuplicateModelCodeException();
        }
    }

    /**
     * null이 아닌 필드만 변경한다.
     */
    private CatalogItem buildUpdatedItem(CatalogItem item, UpdateCatalogItemCommand command) {
        return CatalogItem.builder()
                .id(item.getId())
                .category(item.getCategory())
                .brand(command.brand() != null ? command.brand() : item.getBrand())
                .modelCode(command.modelCode() != null ? command.modelCode() : item.getModelCode())
                .officialImageUrl(command.officialImageUrl() != null ? command.officialImageUrl() : item.getOfficialImageUrl())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(java.time.Instant.now())
                .build();
    }
}
