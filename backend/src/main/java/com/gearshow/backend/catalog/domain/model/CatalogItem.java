package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 카탈로그 아이템 도메인 엔티티 (Aggregate Root).
 *
 * <p>플랫폼에 등록된 공식 장비 정보를 나타낸다.</p>
 * <p>아이템 식별은 스펙 필드 조합(브랜드 + 축구화는 siloName, 유니폼은 clubName + season)으로 한다.</p>
 */
@Getter
public class CatalogItem {

    private final Long id;
    private final Category category;
    private final String brand;
    private final String modelCode;
    private final String officialImageUrl;
    private final CatalogStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private CatalogItem(Long id, Category category, String brand,
                        String modelCode, String officialImageUrl, CatalogStatus status,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.category = category;
        this.brand = brand;
        this.modelCode = modelCode;
        this.officialImageUrl = officialImageUrl;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 필수 필드만으로 새로운 카탈로그 아이템을 생성한다.
     * 최초 상태는 ACTIVE이다.
     *
     * @param category 카테고리
     * @param brand    브랜드명
     * @return 생성된 카탈로그 아이템
     */
    public static CatalogItem create(Category category, String brand) {
        return create(category, brand, null, null);
    }

    /**
     * 새로운 카탈로그 아이템을 생성한다.
     * 최초 상태는 ACTIVE이다.
     *
     * @param category        카테고리
     * @param brand           브랜드명
     * @param modelCode       모델 코드 (nullable)
     * @param officialImageUrl 공식 이미지 URL (nullable)
     * @return 생성된 카탈로그 아이템
     */
    public static CatalogItem create(Category category, String brand,
                                     String modelCode, String officialImageUrl) {
        validate(category, brand);

        Instant now = Instant.now();
        return CatalogItem.builder()
                .category(category)
                .brand(brand)
                .modelCode(modelCode)
                .officialImageUrl(officialImageUrl)
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
     * 카탈로그 아이템 정보를 수정한다.
     * null이 아닌 필드만 변경된다.
     *
     * @param brand           변경할 브랜드명 (null이면 유지)
     * @param modelCode       변경할 모델 코드 (null이면 유지)
     * @param officialImageUrl 변경할 공식 이미지 URL (null이면 유지)
     * @return 수정된 카탈로그 아이템
     */
    public CatalogItem update(String brand, String modelCode, String officialImageUrl) {
        if (brand != null && brand.isBlank()) {
            throw new InvalidCatalogItemException();
        }

        return CatalogItem.builder()
                .id(this.id)
                .category(this.category)
                .brand(brand != null ? brand : this.brand)
                .modelCode(modelCode != null ? modelCode : this.modelCode)
                .officialImageUrl(officialImageUrl != null ? officialImageUrl : this.officialImageUrl)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
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
                .modelCode(this.modelCode)
                .officialImageUrl(this.officialImageUrl)
                .status(CatalogStatus.INACTIVE)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    private static void validate(Category category, String brand) {
        if (category == null || brand == null || brand.isBlank()) {
            throw new InvalidCatalogItemException();
        }
    }
}
