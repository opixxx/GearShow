package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

/**
 * 쇼케이스 등록 커맨드.
 */
public record CreateShowcaseCommand(
        Long ownerId,
        Long catalogItemId,
        String title,
        String description,
        String userSize,
        ConditionGrade conditionGrade,
        int wearCount,
        boolean isForSale,
        int primaryImageIndex,
        boolean hasModelSourceImages
) {}
