package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.CreateShowcaseCommand;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 쇼케이스 등록 요청 DTO.
 * multipart/form-data 요청에서 JSON 필드가 아닌 개별 파라미터로 바인딩된다.
 */
public record CreateShowcaseRequest(
        @NotNull(message = "카탈로그 아이템 ID는 필수입니다")
        Long catalogItemId,

        @NotBlank(message = "제목은 필수입니다")
        @Size(max = 100, message = "제목은 100자 이내여야 합니다")
        String title,

        String description,

        String userSize,

        @NotNull(message = "상태 등급은 필수입니다")
        ConditionGrade conditionGrade,

        Integer wearCount,

        Boolean isForSale,

        Integer primaryImageIndex
) {

    /**
     * 요청을 커맨드로 변환한다.
     *
     * @param ownerId              소유자 ID
     * @param hasModelSourceImages 3D 모델 소스 이미지 포함 여부
     * @return 등록 커맨드
     */
    public CreateShowcaseCommand toCommand(Long ownerId, boolean hasModelSourceImages) {
        return new CreateShowcaseCommand(
                ownerId,
                catalogItemId,
                title,
                description,
                userSize,
                conditionGrade,
                wearCount != null ? wearCount : 0,
                isForSale != null && isForSale,
                primaryImageIndex != null ? primaryImageIndex : 0,
                hasModelSourceImages);
    }
}
