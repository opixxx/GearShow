package com.gearshow.backend.catalog.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 유니폼 스펙 JPA 엔티티.
 */
@Entity
@Table(name = "uniform_spec")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniformSpecJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "uniform_spec_id")
    private Long id;

    @Column(name = "catalog_item_id", nullable = false, unique = true)
    private Long catalogItemId;

    @Column(name = "club_name", nullable = false)
    private String clubName;

    @Column(name = "season", nullable = false)
    private String season;

    @Column(name = "league")
    private String league;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "extra_spec_json", columnDefinition = "json")
    private String extraSpecJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private UniformSpecJpaEntity(Long id, Long catalogItemId, String clubName,
                                 String season, String league, String manufacturer,
                                 String extraSpecJson, LocalDateTime createdAt,
                                 LocalDateTime updatedAt) {
        this.id = id;
        this.catalogItemId = catalogItemId;
        this.clubName = clubName;
        this.season = season;
        this.league = league;
        this.manufacturer = manufacturer;
        this.extraSpecJson = extraSpecJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
