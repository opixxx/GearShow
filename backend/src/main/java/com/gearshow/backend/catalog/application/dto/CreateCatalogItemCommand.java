package com.gearshow.backend.catalog.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;

/**
 * 카탈로그 아이템 등록 커맨드.
 *
 * <p>{@code fullNameKo} / {@code fullNameEn} 은 검색 보강용 (ADR-016) — crawler 가 채우거나
 * 사용자가 직접 입력 시 nullable.</p>
 */
public record CreateCatalogItemCommand(
        Category category,
        String brand,
        String modelCode,
        String officialImageUrl,
        String fullNameKo,
        String fullNameEn,
        BootsSpecCommand bootsSpec,
        UniformSpecCommand uniformSpec
) {

    /**
     * 축구화 스펙 커맨드.
     *
     * <p>{@code siloNameKo} 는 한국어 alias (검색 보강용, nullable).</p>
     */
    public record BootsSpecCommand(
            StudType studType,
            String siloName,
            String siloNameKo,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    /**
     * 유니폼 스펙 커맨드.
     *
     * <p>{@code clubNameKo} 는 한국어 alias, {@code kitType} 은 nullable (ADR-016).</p>
     */
    public record UniformSpecCommand(
            String clubName,
            String clubNameKo,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {}
}
