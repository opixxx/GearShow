package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.KitType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 유니폼 스펙 도메인 엔티티.
 *
 * <p>CATALOG_ITEM Aggregate에 종속되며, 유니폼 카테고리의 상세 스펙을 나타낸다.</p>
 *
 * <p>{@code clubName} 은 영문 canonical (예: "Manchester United"),
 * {@code clubNameKo} 는 한국어 alias (예: "맨체스터 유나이티드") 를 보유한다 (ADR-016).</p>
 *
 * <p>{@code kitType} 은 ADR-016 에 따라 nullable — 빈티지 유니폼 (예: 1988/90 저지) 에는
 * 홈/어웨이/써드 분류가 명시되지 않는 경우가 시장에 존재한다.</p>
 */
@Getter
public class UniformSpec {

    private final Long id;
    private final Long catalogItemId;
    private final String clubName;
    private final String clubNameKo;
    private final String season;
    private final String league;
    /** 홈/어웨이/서드 킷 타입 (nullable — 빈티지 등). */
    private final KitType kitType;
    private final String extraSpecJson;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private UniformSpec(Long id, Long catalogItemId,
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

    /**
     * 필수 필드만으로 새로운 유니폼 스펙을 생성한다 (한국어 alias / kitType 미사용).
     */
    public static UniformSpec create(Long catalogItemId, String clubName, String season) {
        return create(catalogItemId, clubName, null, season, null, null, null);
    }

    /**
     * 새로운 유니폼 스펙을 생성한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID (필수)
     * @param clubName      클럽/국가대표 영문명 (필수)
     * @param clubNameKo    한국어 alias (nullable)
     * @param season        시즌 (필수, 예: "24/25", "1988/90")
     * @param league        리그 (nullable — 국가대표는 null)
     * @param kitType       킷 타입 (nullable — 빈티지는 미명시)
     * @param extraSpecJson 추가 스펙 JSON (nullable)
     * @return 생성된 유니폼 스펙
     */
    public static UniformSpec create(Long catalogItemId,
                                     String clubName, String clubNameKo,
                                     String season, String league, KitType kitType,
                                     String extraSpecJson) {
        validate(catalogItemId, clubName, season);

        Instant now = Instant.now();
        return UniformSpec.builder()
                .catalogItemId(catalogItemId)
                .clubName(clubName)
                .clubNameKo(clubNameKo)
                .season(season)
                .league(league)
                .kitType(kitType)
                .extraSpecJson(extraSpecJson)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 유니폼 스펙 필수값을 검증한다.
     *
     * <p>kitType 은 ADR-016 에 따라 nullable — 빈티지 유니폼 케이스 허용.
     * league 도 nullable — 국가대표는 league 없음.</p>
     */
    private static void validate(Long catalogItemId, String clubName, String season) {
        if (catalogItemId == null
                || clubName == null || clubName.isBlank()
                || season == null || season.isBlank()) {
            throw new InvalidCatalogItemException();
        }
    }
}
