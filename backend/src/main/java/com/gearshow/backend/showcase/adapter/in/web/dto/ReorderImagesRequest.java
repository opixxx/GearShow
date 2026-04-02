package com.gearshow.backend.showcase.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 이미지 정렬 순서 변경 요청 DTO.
 */
public record ReorderImagesRequest(
        @NotEmpty(message = "이미지 정렬 순서 목록은 필수입니다")
        @Valid
        List<ImageOrderItem> imageOrders
) {

    public record ImageOrderItem(
            @NotNull(message = "이미지 ID는 필수입니다")
            Long showcaseImageId,

            int sortOrder,

            boolean isPrimary
    ) {}
}
