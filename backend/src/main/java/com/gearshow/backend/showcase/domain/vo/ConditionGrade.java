package com.gearshow.backend.showcase.domain.vo;

/**
 * 장비 상태 등급.
 *
 * <ul>
 *   <li>S: 미착용 / 새 상품</li>
 *   <li>A: 착용감 있으나 상태 우수</li>
 *   <li>B: 보통 사용감</li>
 *   <li>C: 사용감 많음</li>
 * </ul>
 */
public enum ConditionGrade {

    /** 미착용 / 새 상품 */
    S,

    /** 착용감 있으나 상태 우수 */
    A,

    /** 보통 사용감 */
    B,

    /** 사용감 많음 */
    C
}
