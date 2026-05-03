package com.gearshow.backend.catalog.domain.model;

import com.gearshow.backend.catalog.domain.exception.InvalidCatalogItemException;
import com.gearshow.backend.catalog.domain.vo.StudType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 축구화 스펙 도메인 엔티티.
 *
 * <p>CATALOG_ITEM Aggregate에 종속되며, 축구화 카테고리의 상세 스펙을 나타낸다.</p>
 *
 * <p>{@code siloName} 은 정규화된 영문 canonical (예: "Mercurial Superfly"),
 * {@code siloNameKo} 는 한국어 alias (예: "머큐리얼 슈퍼플라이") 를 보유한다 (ADR-016).
 * 한국어 검색 매칭을 위해 함께 저장하며 nullable.</p>
 */
@Getter
public class BootsSpec {

    private final Long id;
    private final Long catalogItemId;
    private final StudType studType;
    private final String siloName;
    private final String siloNameKo;
    private final String releaseYear;
    private final String surfaceType;
    private final String extraSpecJson;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private BootsSpec(Long id, Long catalogItemId, StudType studType,
                      String siloName, String siloNameKo,
                      String releaseYear, String surfaceType,
                      String extraSpecJson, Instant createdAt,
                      Instant updatedAt) {
        this.id = id;
        this.catalogItemId = catalogItemId;
        this.studType = studType;
        this.siloName = siloName;
        this.siloNameKo = siloNameKo;
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
     * @param studType      스터드 타입 (필수)
     * @param siloName      사일로 영문 canonical (nullable)
     * @param siloNameKo    사일로 한국어 alias (nullable, 검색 보강용)
     * @param releaseYear   출시 연도 (nullable)
     * @param surfaceType   그라운드 타입 (nullable)
     * @param extraSpecJson 추가 스펙 JSON (nullable)
     * @return 생성된 축구화 스펙
     */
    public static BootsSpec create(Long catalogItemId, StudType studType,
                                   String siloName, String siloNameKo,
                                   String releaseYear, String surfaceType,
                                   String extraSpecJson) {
        validate(catalogItemId, studType);
        Instant now = Instant.now();
        return BootsSpec.builder()
                .catalogItemId(catalogItemId)
                .studType(studType)
                .siloName(siloName)
                .siloNameKo(siloNameKo)
                .releaseYear(releaseYear)
                .surfaceType(surfaceType)
                .extraSpecJson(extraSpecJson)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 축구화 스펙 필수값을 검증한다.
     */
    private static void validate(Long catalogItemId, StudType studType) {
        if (catalogItemId == null || studType == null) {
            throw new InvalidCatalogItemException();
        }
    }
}
