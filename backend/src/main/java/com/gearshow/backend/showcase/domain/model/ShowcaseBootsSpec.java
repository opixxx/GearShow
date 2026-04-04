package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.catalog.domain.vo.StudType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 쇼케이스 축구화 스펙 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 축구화 카테고리의 상세 스펙을 나타낸다.</p>
 */
@Getter
public class ShowcaseBootsSpec {

    private final Long id;
    private final Long showcaseId;
    private final StudType studType;
    private final String siloName;
    private final String releaseYear;
    private final String surfaceType;
    private final String extraSpecJson;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private ShowcaseBootsSpec(Long id, Long showcaseId, StudType studType,
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

    /**
     * 새로운 쇼케이스 축구화 스펙을 생성한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param studType   스터드 타입
     * @return 생성된 축구화 스펙
     */
    public static ShowcaseBootsSpec create(Long showcaseId, StudType studType) {
        Instant now = Instant.now();
        return ShowcaseBootsSpec.builder()
                .showcaseId(showcaseId)
                .studType(studType)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
