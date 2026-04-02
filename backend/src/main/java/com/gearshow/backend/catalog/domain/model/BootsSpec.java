package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.vo.StudType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 축구화 스펙 도메인 엔티티.
 *
 * <p>CATALOG_ITEM Aggregate에 종속되며, 축구화 카테고리의 상세 스펙을 나타낸다.</p>
 */
@Getter
public class BootsSpec {

    private final Long id;
    private final Long catalogItemId;
    private final StudType studType;
    private final String siloName;
    private final String releaseYear;
    private final String surfaceType;
    private final String extraSpecJson;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private BootsSpec(Long id, Long catalogItemId, StudType studType,
                      String siloName, String releaseYear, String surfaceType,
                      String extraSpecJson, Instant createdAt,
                      Instant updatedAt) {
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

    /**
     * 새로운 축구화 스펙을 생성한다.
     *
     * @param catalogItemId 카탈로그 아이템 ID
     * @param studType      스터드 타입
     * @return 생성된 축구화 스펙
     */
    public static BootsSpec create(Long catalogItemId, StudType studType) {
        Instant now = Instant.now();
        return BootsSpec.builder()
                .catalogItemId(catalogItemId)
                .studType(studType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
