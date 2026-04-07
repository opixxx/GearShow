package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 쇼케이스 등록 요청 DTO.
 * 클라이언트가 Presigned URL로 S3에 이미지를 직접 업로드한 후, S3 키 목록과 함께 JSON으로 전달한다.
 */
public record CreateShowcaseRequest(
        Long catalogItemId,

        @NotNull(message = "카테고리는 필수입니다")
        Category category,

        @NotBlank(message = "브랜드는 필수입니다")
        String brand,

        String modelCode,

        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 100, message = "제목은 100자 이내여야 합니다")
        String title,

        String description,

        String userSize,

        @NotNull(message = "상태 등급은 필수입니다")
        ConditionGrade conditionGrade,

        Integer wearCount,

        Boolean isForSale,

        Integer primaryImageIndex,

        @NotEmpty(message = "이미지 키 목록은 필수입니다")
        List<String> imageKeys,

        List<String> modelSourceImageKeys,

        // ── 축구화 스펙 (BOOTS) ──
        StudType studType,
        String siloName,
        String releaseYear,
        String surfaceType,

        // ── 유니폼 스펙 (UNIFORM) ──
        String clubName,
        String season,
        String league,
        KitType kitType
) {

    /**
     * 요청을 커맨드로 변환한다.
     *
     * @param ownerId 소유자 ID
     * @return 등록 커맨드
     */
    public CreateShowcaseCommand toCommand(Long ownerId) {
        List<String> safeModelSourceKeys = modelSourceImageKeys != null
                ? modelSourceImageKeys : List.of();
        return new CreateShowcaseCommand(
                ownerId,
                catalogItemId,
                category,
                brand,
                modelCode,
                title,
                description,
                userSize,
                conditionGrade,
                wearCount != null ? wearCount : 0,
                isForSale != null && isForSale,
                primaryImageIndex != null ? primaryImageIndex : 0,
                !safeModelSourceKeys.isEmpty(),
                buildBootsSpec(),
                buildUniformSpec());
    }

    /** 축구화 스펙 필드가 있으면 BootsSpecCommand를 생성한다. */
    private CreateShowcaseCommand.BootsSpecCommand buildBootsSpec() {
        if (studType == null) {
            return null;
        }
        return new CreateShowcaseCommand.BootsSpecCommand(
                studType, siloName, releaseYear, surfaceType, null);
    }

    /** 유니폼 스펙 필드가 있으면 UniformSpecCommand를 생성한다. */
    private CreateShowcaseCommand.UniformSpecCommand buildUniformSpec() {
        if (clubName == null || clubName.isBlank()) {
            return null;
        }
        return new CreateShowcaseCommand.UniformSpecCommand(
                clubName, season, league, kitType, null);
    }
}
