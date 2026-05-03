package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 카탈로그 아이템 등록 요청 DTO.
 *
 * <p>{@code fullNameKo} / {@code fullNameEn} 은 검색 보강용 (ADR-016) — crawler 가 채우거나
 * 사용자가 직접 입력 시 nullable.</p>
 */
public record CreateCatalogItemRequest(
        @NotNull(message = "카테고리는 필수입니다")
        Category category,

        @NotBlank(message = "브랜드는 필수입니다")
        String brand,

        String modelCode,
        String officialImageUrl,
        String fullNameKo,
        String fullNameEn,
        BootsSpecRequest bootsSpec,
        UniformSpecRequest uniformSpec
) {

    public record BootsSpecRequest(
            StudType studType,
            String siloName,
            String siloNameKo,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    /**
     * 유니폼 스펙 요청.
     *
     * <p>{@code kitType} 은 ADR-016 에 따라 nullable — 빈티지 유니폼 케이스 허용.</p>
     */
    public record UniformSpecRequest(
            String clubName,
            String clubNameKo,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {}

    /**
     * 요청을 커맨드로 변환한다.
     */
    public CreateCatalogItemCommand toCommand() {
        return new CreateCatalogItemCommand(
                category, brand, modelCode, officialImageUrl,
                fullNameKo, fullNameEn,
                bootsSpec != null ? new CreateCatalogItemCommand.BootsSpecCommand(
                        bootsSpec.studType, bootsSpec.siloName, bootsSpec.siloNameKo,
                        bootsSpec.releaseYear, bootsSpec.surfaceType,
                        bootsSpec.extraSpecJson) : null,
                uniformSpec != null ? new CreateCatalogItemCommand.UniformSpecCommand(
                        uniformSpec.clubName, uniformSpec.clubNameKo,
                        uniformSpec.season, uniformSpec.league,
                        uniformSpec.kitType, uniformSpec.extraSpecJson) : null
        );
    }
}
