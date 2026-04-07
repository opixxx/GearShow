package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import com.gearshow.backend.showcase.domain.vo.SpecType;

import java.time.Instant;
import java.util.List;

/**
 * 쇼케이스 상세 조회 결과.
 */
public record ShowcaseDetailResult(
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
        List<ImageResult> images,
        Model3dResult model3d,
        SpecResult spec,
        Instant createdAt,
        Instant updatedAt
) {

    /** 이미지 결과. */
    public record ImageResult(
            Long showcaseImageId,
            String imageUrl,
            int sortOrder,
            boolean isPrimary
    ) {
        public static ImageResult from(ShowcaseImage image) {
            return new ImageResult(
                    image.getId(), image.getImageUrl(),
                    image.getSortOrder(), image.isPrimary());
        }
    }

    /** 3D 모델 결과. */
    public record Model3dResult(
            Long showcase3dModelId,
            String modelFileUrl,
            String previewImageUrl,
            ModelStatus modelStatus
    ) {
        public static Model3dResult from(Showcase3dModel model) {
            return new Model3dResult(
                    model.getId(), model.getModelFileUrl(),
                    model.getPreviewImageUrl(), model.getModelStatus());
        }
    }

    /** 스펙 결과 (카테고리별 JSON 데이터 포함). */
    public record SpecResult(
            SpecType specType,
            String specData
    ) {
        public static SpecResult from(ShowcaseSpec spec) {
            return new SpecResult(spec.getSpecType(), spec.getSpecData());
        }
    }

    /**
     * 쇼케이스 상세 결과를 생성한다.
     */
    public static ShowcaseDetailResult of(Showcase showcase,
                                           List<ShowcaseImage> images,
                                           Showcase3dModel model3d,
                                           ShowcaseSpec spec) {
        return new ShowcaseDetailResult(
                showcase.getId(),
                showcase.getOwnerId(),
                showcase.getCatalogItemId(),
                showcase.getCategory(),
                showcase.getBrand(),
                showcase.getModelCode(),
                showcase.getTitle(),
                showcase.getDescription(),
                showcase.getUserSize(),
                showcase.getConditionGrade(),
                showcase.getWearCount(),
                showcase.isForSale(),
                showcase.getStatus(),
                images.stream().map(ImageResult::from).toList(),
                model3d != null ? Model3dResult.from(model3d) : null,
                spec != null ? SpecResult.from(spec) : null,
                showcase.getCreatedAt(),
                showcase.getUpdatedAt());
    }
}
