package com.gearshow.backend.catalog.adapter.in.web.dto;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;

import java.time.Instant;

/**
 * 카탈로그 아이템 상세 조회 응답 DTO.
 *
 * <p>{@code fullNameKo}/{@code fullNameEn}, {@code BootsSpecResponse.siloNameKo},
 * {@code UniformSpecResponse.clubNameKo} 는 ADR-016 한국어 alias — 운영 검수용으로 응답에 노출.</p>
 */
public record CatalogItemDetailResponse(
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String officialImageUrl,
        String fullNameKo,
        String fullNameEn,
        CatalogStatus catalogStatus,
        BootsSpecResponse bootsSpec,
        UniformSpecResponse uniformSpec,
        Instant createdAt
) {

    public record BootsSpecResponse(
            StudType studType,
            String siloName,
            String siloNameKo,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    public record UniformSpecResponse(
            String clubName,
            String clubNameKo,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {}

    public static CatalogItemDetailResponse from(CatalogItemDetailResult result) {
        return new CatalogItemDetailResponse(
                result.catalogItemId(), result.category(), result.brand(),
                result.modelCode(), result.officialImageUrl(),
                result.fullNameKo(), result.fullNameEn(),
                result.catalogStatus(),
                result.bootsSpec() != null ? new BootsSpecResponse(
                        result.bootsSpec().studType(), result.bootsSpec().siloName(),
                        result.bootsSpec().siloNameKo(),
                        result.bootsSpec().releaseYear(), result.bootsSpec().surfaceType(),
                        result.bootsSpec().extraSpecJson()) : null,
                result.uniformSpec() != null ? new UniformSpecResponse(
                        result.uniformSpec().clubName(), result.uniformSpec().clubNameKo(),
                        result.uniformSpec().season(),
                        result.uniformSpec().league(), result.uniformSpec().kitType(),
                        result.uniformSpec().extraSpecJson()) : null,
                result.createdAt());
    }
}
