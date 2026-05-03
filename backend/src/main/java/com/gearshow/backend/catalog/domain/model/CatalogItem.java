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
 *
 * <p>한국어 검색 UX 를 위해 {@code fullNameKo} (한국어 풀네임) / {@code fullNameEn} (영문 풀네임) 을
 * 함께 보유한다. crawler 가 Kream 의 {@code <meta name="keywords">} 에서 추출하여 채우며,
 * 사용자가 직접 입력 시에는 nullable. 이 두 필드는 향후 Showcase 검색의 search_text 합성 소스가 된다 (ADR-016).</p>
 */
@Getter
public class CatalogItem {

    private final Long id;
    private final Category category;
    private final String brand;
    private final String modelCode;
    private final String officialImageUrl;
    private final String fullNameKo;
    private final String fullNameEn;
    private final CatalogStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private CatalogItem(Long id, Category category, String brand,
                        String modelCode, String officialImageUrl,
                        String fullNameKo, String fullNameEn,
                        CatalogStatus status,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.category = category;
        this.brand = brand;
        this.modelCode = modelCode;
        this.officialImageUrl = officialImageUrl;
        this.fullNameKo = fullNameKo;
        this.fullNameEn = fullNameEn;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 필수 필드만으로 새로운 카탈로그 아이템을 생성한다 (한국어/영문 풀네임 미사용).
     */
    public static CatalogItem create(Category category, String brand) {
        return create(category, brand, null, null, null, null);
    }

    /**
     * 새로운 카탈로그 아이템을 생성한다 (한국어/영문 풀네임 미사용).
     */
    public static CatalogItem create(Category category, String brand,
                                     String modelCode, String officialImageUrl) {
        return create(category, brand, modelCode, officialImageUrl, null, null);
    }

    /**
     * 새로운 카탈로그 아이템을 생성한다.
     * 최초 상태는 ACTIVE이다.
     *
     * @param category         카테고리
     * @param brand            브랜드명
     * @param modelCode        모델 코드 (nullable)
     * @param officialImageUrl 공식 이미지 URL (nullable)
     * @param fullNameKo       한국어 풀네임 (nullable, 검색 보강용)
     * @param fullNameEn       영문 풀네임 (nullable, 검색 보강용)
     * @return 생성된 카탈로그 아이템
     */
    public static CatalogItem create(Category category, String brand,
                                     String modelCode, String officialImageUrl,
                                     String fullNameKo, String fullNameEn) {
        validate(category, brand);

        Instant now = Instant.now();
        return CatalogItem.builder()
                .category(category)
                .brand(brand)
                .modelCode(modelCode)
                .officialImageUrl(officialImageUrl)
                .fullNameKo(fullNameKo)
                .fullNameEn(fullNameEn)
                .status(CatalogStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 카탈로그 아이템이 활성 상태인지 확인한다.
     */
    public boolean isActive() {
        return this.status == CatalogStatus.ACTIVE;
    }

    /**
     * 카탈로그 아이템 정보를 수정한다.
     * null인 인자는 변경하지 않는다 (기존값 보존).
     *
     * <p>{@code fullNameKo} / {@code fullNameEn} 정정 경로 — crawler 가 채운 한국어 alias 가
     * 잘못 들어온 경우 admin 이 PATCH 로 보정한다 (ADR-016 §B3).</p>
     */
    public CatalogItem update(String brand, String modelCode, String officialImageUrl,
                              String fullNameKo, String fullNameEn) {
        if (brand != null && brand.isBlank()) {
            throw new InvalidCatalogItemException();
        }

        return CatalogItem.builder()
                .id(this.id)
                .category(this.category)
                .brand(brand != null ? brand : this.brand)
                .modelCode(modelCode != null ? modelCode : this.modelCode)
                .officialImageUrl(officialImageUrl != null ? officialImageUrl : this.officialImageUrl)
                .fullNameKo(fullNameKo != null ? fullNameKo : this.fullNameKo)
                .fullNameEn(fullNameEn != null ? fullNameEn : this.fullNameEn)
                .status(this.status)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * 카탈로그 아이템을 비활성화한다.
     */
    public CatalogItem deactivate() {
        return CatalogItem.builder()
                .id(this.id)
                .category(this.category)
                .brand(this.brand)
                .modelCode(this.modelCode)
                .officialImageUrl(this.officialImageUrl)
                .fullNameKo(this.fullNameKo)
                .fullNameEn(this.fullNameEn)
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
