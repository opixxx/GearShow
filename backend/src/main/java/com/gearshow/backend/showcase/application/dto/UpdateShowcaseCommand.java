package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

/**
 * 쇼케이스 수정 커맨드.
 * null 값은 해당 필드를 변경하지 않는다는 의미이다.
 */
public record UpdateShowcaseCommand(
        String title,
        String description,
        String modelCode,
        String userSize,
        ConditionGrade conditionGrade,
        Integer wearCount,
        Boolean isForSale
) {}
