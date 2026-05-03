package com.gearshow.backend.catalog.application.dto;

/**
 * 카탈로그 아이템 수정 커맨드.
 *
 * <p>null인 필드는 변경하지 않는다 (기존값 보존).</p>
 *
 * <p>{@code fullNameKo} / {@code fullNameEn} 은 ADR-016 의 한국어 alias 정정 경로 —
 * crawler 가 채운 alias 가 잘못 들어왔을 때 admin 이 PATCH 로 보정한다.</p>
 */
public record UpdateCatalogItemCommand(
        String brand,
        String modelCode,
        String officialImageUrl,
        String fullNameKo,
        String fullNameEn
) {
}
