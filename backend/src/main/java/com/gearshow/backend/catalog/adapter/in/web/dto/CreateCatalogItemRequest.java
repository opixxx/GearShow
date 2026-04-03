package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.StudType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 카탈로그 아이템 등록 요청 DTO.
 */
public record CreateCatalogItemRequest(
        @NotNull(message = "카테고리는 필수입니다")
        Category category,

        @NotBlank(message = "브랜드는 필수입니다")
        String brand,

        @NotBlank(message = "아이템 이름은 필수입니다")
        String itemName,

        String modelCode,
        String officialImageUrl,
        BootsSpecRequest bootsSpec,
        UniformSpecRequest uniformSpec
) {

    public record BootsSpecRequest(
            StudType studType,
            String siloName,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    public record UniformSpecRequest(
            String clubName,
            String season,
            String league,
            String extraSpecJson
    ) {}

    /**
     * 요청을 커맨드로 변환한다.
     */
    public CreateCatalogItemCommand toCommand() {
        return new CreateCatalogItemCommand(
                category, brand, itemName, modelCode, officialImageUrl,
                bootsSpec != null ? new CreateCatalogItemCommand.BootsSpecCommand(
                        bootsSpec.studType, bootsSpec.siloName,
                        bootsSpec.releaseYear, bootsSpec.surfaceType,
                        bootsSpec.extraSpecJson) : null,
                uniformSpec != null ? new CreateCatalogItemCommand.UniformSpecCommand(
                        uniformSpec.clubName, uniformSpec.season,
                        uniformSpec.league,
                        uniformSpec.extraSpecJson) : null
        );
    }
}
