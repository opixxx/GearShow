package com.gearshow.backend.showcase.domain.vo;

/**
 * 쇼케이스 부분 수정용 값 객체.
 * null 필드는 변경하지 않음을 의미한다.
 *
 * @param title          변경할 제목 (null이면 유지)
 * @param description    변경할 설명 (null이면 유지)
 * @param modelCode      변경할 모델 코드 (null이면 유지)
 * @param userSize       변경할 사용자 사이즈 (null이면 유지)
 * @param conditionGrade 변경할 상태 등급 (null이면 유지)
 * @param wearCount      변경할 착용 횟수 (null이면 유지)
 * @param forSale        변경할 판매 여부 (null이면 유지)
 */
public record ShowcaseUpdate(
        String title,
        String description,
        String modelCode,
        String userSize,
        ConditionGrade conditionGrade,
        Integer wearCount,
        Boolean forSale
) {
}
