package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.showcase.domain.model.Showcase;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.domain.model.ShowcaseImage;
import com.gearshow.backend.showcase.domain.model.ShowcaseSpec;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import com.gearshow.backend.showcase.domain.vo.SpecType;

import java.time.Instant;
import java.util.List;

/**
 * 쇼케이스 상세 조회 결과.
 *
 * <p><b>ADR-010 Q4-(1)</b>: {@code model3d.modelStatus} 는 {@code showcase_3d_model} 의 프로세스
 * 컬럼이 아니라, {@code model_generation_workflow.current_step} + 완성품 존재 여부로부터
 * 유도된다. application 계층 조립자 ({@code GetShowcaseService}) 가 이 파생을 책임진다.</p>
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

    public record Model3dResult(
            Long showcase3dModelId,
            String modelFileUrl,
            String previewImageUrl,
            ShowcaseModelStatus modelStatus
    ) {
    }

    public record SpecResult(
            SpecType specType,
            String specData
    ) {
        public static SpecResult from(ShowcaseSpec spec) {
            return new SpecResult(spec.getSpecType(), spec.getSpecData());
        }
    }

    public static ShowcaseDetailResult of(Showcase showcase,
                                          List<ShowcaseImage> images,
                                          Showcase3dModel model3d,
                                          ShowcaseModelStatus model3dStatus,
                                          ShowcaseSpec spec) {
        Model3dResult model3dResult = resolveModel3dResult(model3d, model3dStatus);
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
                model3dResult,
                spec != null ? SpecResult.from(spec) : null,
                showcase.getCreatedAt(),
                showcase.getUpdatedAt());
    }

    private static Model3dResult resolveModel3dResult(Showcase3dModel model3d,
                                                      ShowcaseModelStatus status) {
        if (model3d == null && status == ShowcaseModelStatus.NONE) {
            return null;
        }
        Long id = model3d != null ? model3d.getId() : null;
        String modelFileUrl = model3d != null ? model3d.getModelFileUrl() : null;
        String previewImageUrl = model3d != null ? model3d.getPreviewImageUrl() : null;
        return new Model3dResult(id, modelFileUrl, previewImageUrl, status);
    }
}
