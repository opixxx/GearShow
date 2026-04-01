package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ShowcaseStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쇼케이스 JPA 엔티티.
 */
@Entity
@Table(name = "showcase")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_id")
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "catalog_item_id", nullable = false)
    private Long catalogItemId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "user_size")
    private String userSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_grade", nullable = false)
    private ConditionGrade conditionGrade;

    @Column(name = "wear_count")
    private int wearCount;

    @Column(name = "is_for_sale", nullable = false)
    private boolean forSale;

    @Enumerated(EnumType.STRING)
    @Column(name = "showcase_status", nullable = false)
    private ShowcaseStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ShowcaseJpaEntity(Long id, Long ownerId, Long catalogItemId, String title,
                              String description, String userSize, ConditionGrade conditionGrade,
                              int wearCount, boolean forSale, ShowcaseStatus status,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.catalogItemId = catalogItemId;
        this.title = title;
        this.description = description;
        this.userSize = userSize;
        this.conditionGrade = conditionGrade;
        this.wearCount = wearCount;
        this.forSale = forSale;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
