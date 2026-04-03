package com.gearshow.backend.catalog.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.KitType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "kit_type", nullable = false)
    private KitType kitType;

    @Column(name = "extra_spec_json", columnDefinition = "json")
    private String extraSpecJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private UniformSpecJpaEntity(Long id, Long catalogItemId, String clubName,
                                 String season, String league, KitType kitType,
                                 String extraSpecJson, Instant createdAt,
                                 Instant updatedAt) {
        this.id = id;
        this.catalogItemId = catalogItemId;
        this.clubName = clubName;
        this.season = season;
        this.league = league;
        this.kitType = kitType;
        this.extraSpecJson = extraSpecJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
