package com.gearshow.backend.catalog.application.dto;

import com.gearshow.backend.catalog.domain.model.BootsSpec;
import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.model.UniformSpec;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;

import java.time.Instant;

/**
 * 카탈로그 아이템 상세 조회 결과.
 *
 * <p>한국어/영문 풀네임({@code fullNameKo} / {@code fullNameEn}), 사일로 한국어 alias
 * ({@code BootsSpecResult.siloNameKo}), 클럽 한국어 alias
 * ({@code UniformSpecResult.clubNameKo}) 를 응답으로 노출한다 (ADR-016 §B2 검토 반영).
 * 운영자/관리자 도구가 crawler 가 채운 한국어 alias 를 시각 검증할 수 있어야 하기 때문.</p>
 */
public record CatalogItemDetailResult(
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String officialImageUrl,
        String fullNameKo,
        String fullNameEn,
        CatalogStatus catalogStatus,
        BootsSpecResult bootsSpec,
        UniformSpecResult uniformSpec,
        Instant createdAt
) {

    public record BootsSpecResult(
            StudType studType,
            String siloName,
            String siloNameKo,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {
        public static BootsSpecResult from(BootsSpec spec) {
            return new BootsSpecResult(
                    spec.getStudType(), spec.getSiloName(), spec.getSiloNameKo(),
                    spec.getReleaseYear(), spec.getSurfaceType(),
                    spec.getExtraSpecJson());
        }
    }

    public record UniformSpecResult(
            String clubName,
            String clubNameKo,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {
        public static UniformSpecResult from(UniformSpec spec) {
            return new UniformSpecResult(
                    spec.getClubName(), spec.getClubNameKo(),
                    spec.getSeason(), spec.getLeague(), spec.getKitType(),
                    spec.getExtraSpecJson());
        }
    }

    public static CatalogItemDetailResult of(CatalogItem item, BootsSpec bootsSpec, UniformSpec uniformSpec) {
        return new CatalogItemDetailResult(
                item.getId(), item.getCategory(), item.getBrand(),
                item.getModelCode(), item.getOfficialImageUrl(),
                item.getFullNameKo(), item.getFullNameEn(),
                item.getStatus(),
                bootsSpec != null ? BootsSpecResult.from(bootsSpec) : null,
                uniformSpec != null ? UniformSpecResult.from(uniformSpec) : null,
                item.getCreatedAt());
    }
}
