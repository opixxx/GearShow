package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.BulkImportCatalogItemCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 카탈로그 아이템 일괄 등록 요청 DTO.
 *
 * <p>기존 단건 등록 요청({@link CreateCatalogItemRequest})을 그대로 재사용한다.</p>
 */
public record BulkImportCatalogItemRequest(
        @NotEmpty(message = "items는 최소 1건 이상이어야 합니다")
        @Size(max = 1000, message = "한 번에 1000건까지만 처리할 수 있습니다")
        @Valid
        List<CreateCatalogItemRequest> items
) {

    /**
     * 요청을 일괄 등록 커맨드로 변환한다.
     */
    public BulkImportCatalogItemCommand toCommand() {
        return new BulkImportCatalogItemCommand(
                items.stream().map(CreateCatalogItemRequest::toCommand).toList()
        );
    }
}
