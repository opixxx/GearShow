package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.UpdateCatalogItemCommand;

/**
 * 카탈로그 아이템 수정 요청 DTO.
 */
public record UpdateCatalogItemRequest(
        String brand,
        String modelCode,
        String officialImageUrl
) {

    /**
     * 요청을 커맨드로 변환한다.
     */
    public UpdateCatalogItemCommand toCommand() {
        return new UpdateCatalogItemCommand(brand, modelCode, officialImageUrl);
    }
}
