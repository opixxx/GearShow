package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseException;
import com.gearshow.backend.showcase.domain.vo.SpecType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 쇼케이스 스펙 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 카테고리별 장비 상세 스펙을 JSON으로 관리한다.
 * specType으로 스펙 종류를 구분하고, specData에 JSON 형태의 상세 정보를 저장한다.</p>
 */
@Getter
public class ShowcaseSpec {

    private final Long id;
    private final Long showcaseId;
    private final SpecType specType;
    private final String specData;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private ShowcaseSpec(Long id, Long showcaseId, SpecType specType,
                         String specData, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.specType = specType;
        this.specData = specData;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 새로운 쇼케이스 스펙을 생성한다.
     *
     * @param showcaseId 쇼케이스 ID
     * @param specType   스펙 타입
     * @param specData   JSON 형태의 스펙 데이터
     * @return 생성된 쇼케이스 스펙
     */
    public static ShowcaseSpec create(Long showcaseId, SpecType specType, String specData) {
        validate(showcaseId, specType, specData);
        Instant now = Instant.now();
        return ShowcaseSpec.builder()
                .showcaseId(showcaseId)
                .specType(specType)
                .specData(specData)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 스펙 데이터를 변경한다.
     *
     * @param newSpecData 새로운 JSON 형태의 스펙 데이터
     * @return 변경된 쇼케이스 스펙
     */
    public ShowcaseSpec updateSpecData(String newSpecData) {
        if (newSpecData == null || newSpecData.isBlank()) {
            throw new InvalidShowcaseException();
        }
        return ShowcaseSpec.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .specType(this.specType)
                .specData(newSpecData)
                .createdAt(this.createdAt)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * 필수 파라미터를 검증한다.
     */
    private static void validate(Long showcaseId, SpecType specType, String specData) {
        if (showcaseId == null || specType == null
                || specData == null || specData.isBlank()) {
            throw new InvalidShowcaseException();
        }
    }
}
