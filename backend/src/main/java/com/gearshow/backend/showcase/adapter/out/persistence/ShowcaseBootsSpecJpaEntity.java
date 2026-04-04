package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.StudType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 축구화 스펙 JPA 엔티티.
 */
@Entity
@Table(name = "showcase_boots_spec")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseBootsSpecJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_boots_spec_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false, unique = true)
    private Long showcaseId;

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
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private ShowcaseBootsSpecJpaEntity(Long id, Long showcaseId, StudType studType,
                                       String siloName, String releaseYear, String surfaceType,
                                       String extraSpecJson, Instant createdAt,
                                       Instant updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.studType = studType;
        this.siloName = siloName;
        this.releaseYear = releaseYear;
        this.surfaceType = surfaceType;
        this.extraSpecJson = extraSpecJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
