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

    /** 클럽/국가대표 한국어 alias (검색 보강용 — ADR-016) */
    @Column(name = "club_name_ko")
    private String clubNameKo;

    @Column(name = "season", nullable = false)
    private String season;

    @Column(name = "league")
    private String league;

    /**
     * 킷 타입 (홈/어웨이/써드).
     *
     * <p>ADR-016: nullable — 빈티지 유니폼 (예: 1988/90 저지) 에 미명시되는 케이스 시장 존재.
     * 운영 DB 마이그레이션 시 기존 NOT NULL 제약 해제 필요:
     * {@code ALTER TABLE uniform_spec MODIFY kit_type VARCHAR(20) NULL;}</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kit_type")
    private KitType kitType;

    @Column(name = "extra_spec_json", columnDefinition = "json")
    private String extraSpecJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private UniformSpecJpaEntity(Long id, Long catalogItemId,
                                 String clubName, String clubNameKo,
                                 String season, String league, KitType kitType,
                                 String extraSpecJson, Instant createdAt,
                                 Instant updatedAt) {
        this.id = id;
        this.catalogItemId = catalogItemId;
        this.clubName = clubName;
        this.clubNameKo = clubNameKo;
        this.season = season;
        this.league = league;
        this.kitType = kitType;
        this.extraSpecJson = extraSpecJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
