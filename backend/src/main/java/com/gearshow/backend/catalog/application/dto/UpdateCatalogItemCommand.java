package com.gearshow.backend.catalog.application.dto;

/**
 * 카탈로그 아이템 수정 커맨드.
 * null인 필드는 변경하지 않는다.
 */
public record UpdateCatalogItemCommand(
        String brand,
        String modelCode,
        String officialImageUrl
) {
}
