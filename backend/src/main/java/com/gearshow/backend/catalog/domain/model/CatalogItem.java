package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 카탈로그 아이템 도메인 엔티티 (Aggregate Root).
 *
 * <p>플랫폼에 등록된 공식 장비 정보를 나타낸다.</p>
 */
@Getter
public class CatalogItem {

    private final Long id;
    private final Category category;
    private final String brand;
    private final String itemName;
    private final String modelCode;
    private final String officialImageUrl;
    private final CatalogStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    @Builder
    private CatalogItem(Long id, Category category, String brand, String itemName,
                        String modelCode, String officialImageUrl, CatalogStatus status,
                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.category = category;
        this.brand = brand;
        this.itemName = itemName;
        this.modelCode = modelCode;
        this.officialImageUrl = officialImageUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 카탈로그 아이템을 생성한다.
     * 최초 상태는 ACTIVE이다.
     *
     * @param category 카테고리
     * @param brand    브랜드명
     * @param itemName 아이템 이름
     * @return 생성된 카탈로그 아이템
     */
    public static CatalogItem create(Category category, String brand, String itemName) {
        validate(category, brand, itemName);

        LocalDateTime now = LocalDateTime.now();
        return CatalogItem.builder()
                .category(category)
                .brand(brand)
                .itemName(itemName)
                .status(CatalogStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 카탈로그 아이템이 활성 상태인지 확인한다.
     *
     * @return 활성 여부
     */
    public boolean isActive() {
        return this.status == CatalogStatus.ACTIVE;
    }

    /**
     * 카탈로그 아이템을 비활성화한다.
     *
     * @return 비활성화된 카탈로그 아이템
     */
    public CatalogItem deactivate() {
        return CatalogItem.builder()
                .id(this.id)
                .category(this.category)
                .brand(this.brand)
                .itemName(this.itemName)
                .modelCode(this.modelCode)
                .officialImageUrl(this.officialImageUrl)
                .status(CatalogStatus.INACTIVE)
                .createdAt(this.createdAt)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private static void validate(Category category, String brand, String itemName) {
        if (category == null
                || brand == null || brand.isBlank()
                || itemName == null || itemName.isBlank()) {
            throw new InvalidCatalogItemException();
        }
    }
}
