package com.gearshow.backend.catalog.application.port.out;

import com.gearshow.backend.catalog.domain.model.CatalogItem;
import com.gearshow.backend.catalog.domain.vo.Category;

import java.time.Instant;
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
     * 카테고리와 브랜드 조건에 해당하는 카탈로그 아이템 ID 목록을 조회한다.
     *
     * @param category 카테고리 필터 (null이면 전체)
     * @param brand    브랜드 필터 (null이면 전체)
     * @return 카탈로그 아이템 ID 목록
     */
    List<Long> findIdsByCategoryAndBrand(Category category, String brand);

    /**
     * 첫 페이지 카탈로그 아이템 목록을 조회한다.
     *
     * @param size 조회 개수 (hasNext 판단을 위해 size + 1 조회)
     * @return 조회된 카탈로그 아이템 목록
     */
    List<CatalogItem> findAllFirstPage(int size);

    /**
     * 커서 기반으로 카탈로그 아이템 목록을 조회한다 (Keyset Pagination).
     * 정렬: createdAt DESC, id DESC.
     *
     * @param cursorCreatedAt 커서 생성 시각
     * @param cursorId        커서 ID
     * @param size            조회 개수 (hasNext 판단을 위해 size + 1 조회)
     * @return 조회된 카탈로그 아이템 목록
     */
    List<CatalogItem> findAllWithCursor(Instant cursorCreatedAt, Long cursorId, int size);
}
