package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.catalog.domain.vo.KitType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 유니폼 스펙 JPA 엔티티.
 */
@Entity
@Table(name = "showcase_uniform_spec")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseUniformSpecJpaEntity extends BaseShowcaseSpecJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_uniform_spec_id")
    private Long id;

    @Column(name = "club_name", nullable = false)
    private String clubName;

    @Column(name = "season", nullable = false)
    private String season;

    @Column(name = "league")
    private String league;

    @Enumerated(EnumType.STRING)
    @Column(name = "kit_type", nullable = false)
    private KitType kitType;

    @Builder
    private ShowcaseUniformSpecJpaEntity(Long id, Long showcaseId, String clubName,
                                         String season, String league, KitType kitType,
                                         String extraSpecJson, Instant createdAt,
                                         Instant updatedAt) {
        super(showcaseId, extraSpecJson, createdAt, updatedAt);
        this.id = id;
        this.clubName = clubName;
        this.season = season;
        this.league = league;
        this.kitType = kitType;
    }
}
