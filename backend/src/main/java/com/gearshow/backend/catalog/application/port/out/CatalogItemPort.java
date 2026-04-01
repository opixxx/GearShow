package com.gearshow.backend.catalog.application.port.out;

import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.Category;

import java.util.List;
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

    /**
     * 커서 기반으로 카탈로그 아이템 목록을 조회한다.
     *
     * @param cursorId  커서 ID (null이면 첫 페이지)
     * @param size      조회 개수 (hasNext 판단을 위해 size + 1 조회)
     * @param category  카테고리 필터 (null이면 전체)
     * @param brand     브랜드 필터 (null이면 전체)
     * @param keyword   아이템명/모델코드 검색 (null이면 전체)
     * @return 조회된 카탈로그 아이템 목록
     */
    List<CatalogItem> findAllWithCursor(Long cursorId, int size,
                                        Category category, String brand, String keyword);
}
