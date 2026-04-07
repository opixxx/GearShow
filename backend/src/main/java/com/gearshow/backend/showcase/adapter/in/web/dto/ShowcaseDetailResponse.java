package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.application.dto.ShowcaseDetailResult;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import com.gearshow.backend.showcase.domain.vo.SpecType;

import java.time.Instant;
import java.util.List;

/**
 * 쇼케이스 상세 조회 응답 DTO.
 */
public record ShowcaseDetailResponse(
        Long showcaseId,
        Long ownerId,
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String title,
        String description,
        String userSize,
        ConditionGrade conditionGrade,
        int wearCount,
        boolean isForSale,
        ShowcaseStatus showcaseStatus,
        List<ImageResponse> images,
        Model3dResponse model3d,
        SpecResponse spec,
        Instant createdAt,
        Instant updatedAt
) {

    public record ImageResponse(
            Long showcaseImageId,
            String imageUrl,
            int sortOrder,
            boolean isPrimary
    ) {}

    public record Model3dResponse(
            Long showcase3dModelId,
            String modelFileUrl,
            String previewImageUrl,
            ModelStatus modelStatus
    ) {}

    /** 스펙 응답 (specType으로 타입 구분, specData에 JSON 상세 정보). */
    public record SpecResponse(
            SpecType specType,
            String specData
    ) {}

    /**
     * 결과를 응답으로 변환한다.
     */
    public static ShowcaseDetailResponse from(ShowcaseDetailResult result) {
        List<ImageResponse> images = result.images().stream()
                .map(i -> new ImageResponse(
                        i.showcaseImageId(), i.imageUrl(),
                        i.sortOrder(), i.isPrimary()))
                .toList();

        Model3dResponse model3d = result.model3d() != null
                ? new Model3dResponse(
                        result.model3d().showcase3dModelId(),
                        result.model3d().modelFileUrl(),
                        result.model3d().previewImageUrl(),
                        result.model3d().modelStatus())
                : null;

        SpecResponse spec = result.spec() != null
                ? new SpecResponse(
                        result.spec().specType(),
                        result.spec().specData())
                : null;

        return new ShowcaseDetailResponse(
                result.showcaseId(), result.ownerId(), result.catalogItemId(),
                result.category(), result.brand(), result.modelCode(),
                result.title(), result.description(), result.userSize(),
                result.conditionGrade(), result.wearCount(), result.isForSale(),
                result.showcaseStatus(), images, model3d, spec,
                result.createdAt(), result.updatedAt());
    }
}
