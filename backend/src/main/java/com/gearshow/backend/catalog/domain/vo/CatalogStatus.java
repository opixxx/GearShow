package com.gearshow.backend.catalog.domain.vo;

/**
 * 카탈로그 아이템의 상태.
 *
 * <ul>
 *   <li>ACTIVE: 정상 노출, 쇼케이스 등록 가능</li>
 *   <li>INACTIVE: 비공개, 신규 쇼케이스 등록 불가</li>
 * </ul>
 */
public enum CatalogStatus {

    /** 정상 노출 */
    ACTIVE,

    /** 비공개 */
    INACTIVE
}
