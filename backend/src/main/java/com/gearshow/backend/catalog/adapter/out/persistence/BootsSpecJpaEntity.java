package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.StudType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 축구화 스펙 JPA 엔티티.
 */
@Entity
@Table(name = "boots_spec")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BootsSpecJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "boots_spec_id")
    private Long id;

    @Column(name = "catalog_item_id", nullable = false, unique = true)
    private Long catalogItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stud_type", nullable = false)
    private StudType studType;

    @Column(name = "silo_name")
    private String siloName;

    @Column(name = "release_year")
    private String releaseYear;

    @Column(name = "surface_type")
    private String surfaceType;

    @Column(name = "extra_spec_json", columnDefinition = "json")
    private String extraSpecJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private BootsSpecJpaEntity(Long id, Long catalogItemId, StudType studType,
                               String siloName, String releaseYear, String surfaceType,
                               String extraSpecJson, LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
        this.id = id;
        this.catalogItemId = catalogItemId;
        this.studType = studType;
        this.siloName = siloName;
        this.releaseYear = releaseYear;
        this.surfaceType = surfaceType;
        this.extraSpecJson = extraSpecJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
