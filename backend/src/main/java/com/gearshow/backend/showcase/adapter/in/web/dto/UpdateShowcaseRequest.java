package com.gearshow.backend.showcase.adapter.in.web.dto;

import com.gearshow.backend.showcase.application.dto.UpdateShowcaseCommand;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 쇼케이스 수정 요청 DTO.
 */
public record UpdateShowcaseRequest(
        @Size(max = 100, message = "제목은 100자 이내여야 합니다")
        String title,

        String description,

        String userSize,

        ConditionGrade conditionGrade,

        @PositiveOrZero(message = "착용 횟수는 0 이상이어야 합니다.")
        Integer wearCount,

        Boolean isForSale
) {

    /**
     * 요청을 커맨드로 변환한다.
     */
    public UpdateShowcaseCommand toCommand() {
        return new UpdateShowcaseCommand(
                title, description, userSize,
                conditionGrade, wearCount, isForSale);
    }
}
