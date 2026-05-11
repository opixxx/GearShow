package com.gearshow.backend.catalog.application.port.in;

import com.gearshow.backend.catalog.application.dto.CatalogItemListResult;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;

/**
 * 카탈로그 아이템 목록 조회 유스케이스.
 */
public interface ListCatalogItemsUseCase {

    /**
     * 카테고리와 키워드 조건으로 카탈로그 아이템 목록을 커서 기반 페이징으로 조회한다.
     *
     * @param category  카테고리 필터 (null이면 전체)
     * @param keyword   부분일치 키워드 (null/blank이면 전체).
     *                  brand/modelCode/fullNameKo/fullNameEn 컬럼에 OR LIKE 매칭
     * @param pageToken 페이지 토큰 (null이면 첫 페이지)
     * @param size      페이지 크기
     * @return 커서 페이징 응답
     */
    PageInfo<CatalogItemListResult> list(Category category, String keyword, String pageToken, int size);
}
