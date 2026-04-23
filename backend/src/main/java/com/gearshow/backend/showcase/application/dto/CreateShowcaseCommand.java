package com.gearshow.backend.showcase.application.dto;

import com.gearshow.backend.catalog.domain.vo.Category;
import com.gearshow.backend.catalog.domain.vo.KitType;
import com.gearshow.backend.catalog.domain.vo.StudType;
import com.gearshow.backend.showcase.domain.vo.ConditionGrade;
import com.gearshow.backend.showcase.domain.vo.ContentHash;

/**
 * 쇼케이스 등록 커맨드.
 *
 * @param catalogItemId 카탈로그 아이템 ID (선택, null 허용)
 * @param category      카테고리 (필수)
 * @param brand         브랜드명 (필수)
 * @param modelCode     모델 코드 (선택)
 */
public record CreateShowcaseCommand(
        Long ownerId,
        Long catalogItemId,
        Category category,
        String brand,
        String modelCode,
        String title,
        String description,
        String userSize,
        ConditionGrade conditionGrade,
        int wearCount,
        boolean isForSale,
        int primaryImageIndex,
        boolean hasModelSourceImages,
        BootsSpecCommand bootsSpec,
        UniformSpecCommand uniformSpec,
        /** 이미지 조합의 SHA-256 해시. 10분 창 내 중복 등록 감지용. null 허용. */
        ContentHash contentHash,
        /**
         * API {@code Idempotency-Key} 헤더 값 (ADR-011 ①).
         * 컨트롤러가 필수화했으므로 non-null / non-blank. 이후 Outbox {@code event_id} 의
         * 결정적 파생(ADR-011 ③)과 {@code model_generation_workflow.idempotency_key} UNIQUE 식별자로 사용한다.
         */
        String idempotencyKey
) {

    /**
     * 축구화 스펙 커맨드.
     */
    public record BootsSpecCommand(
            StudType studType,
            String siloName,
            String releaseYear,
            String surfaceType,
            String extraSpecJson
    ) {}

    /**
     * 유니폼 스펙 커맨드.
     */
    public record UniformSpecCommand(
            String clubName,
            String season,
            String league,
            KitType kitType,
            String extraSpecJson
    ) {}
}
