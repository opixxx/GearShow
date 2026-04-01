package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.CatalogStatus;
import com.gearshow.backend.catalog.domain.vo.Category;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 카탈로그 아이템 JPA 엔티티.
 */
@Entity
@Table(name = "catalog_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "catalog_item_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Category category;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "model_code")
    private String modelCode;

    @Column(name = "official_image_url")
    private String officialImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "catalog_status", nullable = false)
    private CatalogStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private CatalogItemJpaEntity(Long id, Category category, String brand, String itemName,
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
}
