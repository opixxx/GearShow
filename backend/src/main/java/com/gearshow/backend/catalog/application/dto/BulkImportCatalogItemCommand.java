package com.gearshow.backend.catalog.application.dto;

import java.util.List;

/**
 * 카탈로그 아이템 일괄 등록 커맨드.
 *
 * <p>각 항목은 단건 등록 커맨드(@link CreateCatalogItemCommand)를 그대로 재사용한다.</p>
 */
public record BulkImportCatalogItemCommand(
        List<CreateCatalogItemCommand> items
) {
}
