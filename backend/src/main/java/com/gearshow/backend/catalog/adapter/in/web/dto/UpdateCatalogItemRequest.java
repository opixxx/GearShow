package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.UpdateCatalogItemCommand;
import jakarta.validation.constraints.Size;

/**
 * 카탈로그 아이템 수정 요청 DTO.
 *
 * <p>{@code fullNameKo} / {@code fullNameEn} 은 ADR-016 한국어 alias 정정 경로.
 * null 인 필드는 변경하지 않는다.</p>
 */
public record UpdateCatalogItemRequest(
        @Size(max = 100, message = "브랜드는 100자 이하여야 합니다")
        String brand,

        @Size(max = 100, message = "모델 코드는 100자 이하여야 합니다")
        String modelCode,

        String officialImageUrl,

        @Size(max = 255, message = "한국어 풀네임은 255자 이하여야 합니다")
        String fullNameKo,

        @Size(max = 255, message = "영문 풀네임은 255자 이하여야 합니다")
        String fullNameEn
) {

    /**
     * 요청을 커맨드로 변환한다.
     */
    public UpdateCatalogItemCommand toCommand() {
        return new UpdateCatalogItemCommand(brand, modelCode, officialImageUrl, fullNameKo, fullNameEn);
    }
}
