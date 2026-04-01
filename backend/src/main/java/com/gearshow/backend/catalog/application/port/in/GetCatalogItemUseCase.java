package com.gearshow.backend.catalog.application.port.in;

import com.gearshow.backend.catalog.application.dto.CatalogItemDetailResult;

/**
 * 카탈로그 아이템 상세 조회 유스케이스.
 */
public interface GetCatalogItemUseCase {

    /**
     * 카탈로그 아이템 상세를 조회한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @return 상세 조회 결과
     */
    CatalogItemDetailResult getCatalogItem(Long catalogItemId);
}
