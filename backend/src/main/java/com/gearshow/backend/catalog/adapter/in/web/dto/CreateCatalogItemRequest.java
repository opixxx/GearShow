package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.CreateCatalogItemCommand;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        @Size(max = 100, message = "브랜드는 100자 이하여야 합니다")
        String brand,

        @Size(max = 100, message = "모델 코드는 100자 이하여야 합니다")
        String modelCode,

        String officialImageUrl,

        @Size(max = 255, message = "한국어 풀네임은 255자 이하여야 합니다")
        String fullNameKo,

        @Size(max = 255, message = "영문 풀네임은 255자 이하여야 합니다")
        String fullNameEn,

        BootsSpecRequest bootsSpec,
        UniformSpecRequest uniformSpec
) {

    public record BootsSpecRequest(
            StudType studType,

            @Size(max = 255, message = "사일로명은 255자 이하여야 합니다")
            String siloName,

            @Size(max = 255, message = "사일로 한국어 alias 는 255자 이하여야 합니다")
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
            @Size(max = 255, message = "클럽명은 255자 이하여야 합니다")
            String clubName,

            @Size(max = 255, message = "클럽 한국어 alias 는 255자 이하여야 합니다")
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
