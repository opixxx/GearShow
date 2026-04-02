package com.gearshow.backend.catalog.application.port.in;

import com.gearshow.backend.catalog.application.dto.CatalogItemListResult;
import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.common.dto.PageInfo;

/**
 * 카탈로그 아이템 목록 조회 유스케이스.
 */
public interface ListCatalogItemsUseCase {

    /**
     * 커서 기반으로 카탈로그 아이템 목록을 조회한다.
     *
     * @param pageToken 페이지 토큰 (null이면 첫 페이지)
     * @param size      페이지 크기
     * @param category  카테고리 필터
     * @param brand     브랜드 필터
     * @param keyword   검색어
     * @return 커서 페이징 응답
     */
    PageInfo<CatalogItemListResult> list(
            String pageToken, int size, Category category, String brand, String keyword);
}
