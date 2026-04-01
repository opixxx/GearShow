package com.gearshow.backend.catalog.domain.repository;

import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.Category;

import java.util.Optional;

/**
 * 카탈로그 아이템 도메인 저장소 인터페이스.
 */
public interface CatalogItemRepository {

    /**
     * 카탈로그 아이템을 저장한다.
     *
     * @param catalogItem 저장할 카탈로그 아이템
     * @return 저장된 카탈로그 아이템
     */
    CatalogItem save(CatalogItem catalogItem);

    /**
     * ID로 카탈로그 아이템을 조회한다.
     *
     * @param id 카탈로그 아이템 ID
     * @return 카탈로그 아이템 Optional
     */
    Optional<CatalogItem> findById(Long id);

    /**
     * 동일 카테고리 내 모델 코드 중복 여부를 확인한다.
     *
     * @param category  카테고리
     * @param modelCode 모델 코드
     * @return 중복 여부
     */
    boolean existsByCategoryAndModelCode(Category category, String modelCode);
}
