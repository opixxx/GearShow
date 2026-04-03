package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.vo.KitType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 유니폼 스펙 도메인 엔티티.
 *
 * <p>CATALOG_ITEM Aggregate에 종속되며, 유니폼 카테고리의 상세 스펙을 나타낸다.</p>
 */
@Getter
public class UniformSpec {

    private final Long id;
    private final Long catalogItemId;
    private final String clubName;
    private final String season;
    private final String league;
    /** 홈/어웨이/서드 킷 타입 */
    private final KitType kitType;
    private final String extraSpecJson;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private UniformSpec(Long id, Long catalogItemId, String clubName,
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

    /**
     * 새로운 유니폼 스펙을 생성한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @param clubName      클럽 이름
     * @param season        시즌
     * @param kitType       킷 타입 (HOME/AWAY/THIRD)
     * @return 생성된 유니폼 스펙
     */
    public static UniformSpec create(Long catalogItemId, String clubName, String season, KitType kitType) {
        Instant now = Instant.now();
        return UniformSpec.builder()
                .catalogItemId(catalogItemId)
                .clubName(clubName)
                .season(season)
                .kitType(kitType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
