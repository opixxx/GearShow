package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 카탈로그 아이템 JPA 저장소.
 */
public interface CatalogItemJpaRepository extends JpaRepository<CatalogItemJpaEntity, Long> {

    /**
     * 동일 카테고리 내 모델 코드 중복 여부를 확인한다.
     *
     * @param category  카테고리
     * @param modelCode 모델 코드
     * @return 중복 여부
     */
    boolean existsByCategoryAndModelCode(Category category, String modelCode);
}
