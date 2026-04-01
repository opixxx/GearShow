package com.gearshow.backend.catalog.application.port.out;

import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.Category;

import java.util.Optional;

/**
 * 카탈로그 아이템 Outbound Port.
 */
public interface CatalogItemPort {

    /**
     * 카탈로그 아이템을 저장한다.
     */
    CatalogItem save(CatalogItem catalogItem);

    /**
     * ID로 카탈로그 아이템을 조회한다.
     */
    Optional<CatalogItem> findById(Long id);

    /**
     * 동일 카테고리 내 모델 코드 중복 여부를 확인한다.
     */
    boolean existsByCategoryAndModelCode(Category category, String modelCode);
}
