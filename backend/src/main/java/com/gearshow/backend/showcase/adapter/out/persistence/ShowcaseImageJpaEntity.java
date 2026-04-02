package com.gearshow.backend.showcase.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 이미지 JPA 엔티티.
 */
@Entity
@Table(name = "showcase_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_image_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false)
    private Long showcaseId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private ShowcaseImageJpaEntity(Long id, Long showcaseId, String imageUrl,
                                   int sortOrder, boolean primary, Instant createdAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.primary = primary;
        this.createdAt = createdAt;
    }
}
