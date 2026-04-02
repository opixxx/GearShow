package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

/**
 * 쇼케이스 수정 커맨드.
 */
public record UpdateShowcaseCommand(
        String title,
        String description,
        String userSize,
        ConditionGrade conditionGrade,
        Integer wearCount,
        Boolean isForSale
) {}
