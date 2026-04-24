package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.vo.AngleType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 3D 모델 소스 이미지 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate 에 종속되며, 3D 모델 생성에 필요한 원본 이미지이다. 최소 4장
 * (앞/뒤/좌/우) 이 필요하다.</p>
 *
 * <p><b>ADR-010 변경</b>: 원래는 {@code showcase_3d_model_id} FK 로 연결됐으나 Showcase3dModel
 * 이 "완성품" 전용으로 축소되면서 요청 시점에 존재하지 않는다. 본 엔티티는 쇼케이스 단위로
 * 귀속되므로 {@code showcase_id} 를 직접 참조한다.</p>
 */
@Getter
public class ModelSourceImage {

    private final Long id;
    private final Long showcaseId;
    private final String imageUrl;
    private final AngleType angleType;
    private final int sortOrder;
    private final Instant createdAt;

    @Builder
    private ModelSourceImage(Long id, Long showcaseId, String imageUrl,
                             AngleType angleType, int sortOrder,
                             Instant createdAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.imageUrl = imageUrl;
        this.angleType = angleType;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    public static ModelSourceImage create(Long showcaseId, String imageUrl,
                                          AngleType angleType, int sortOrder) {
        return ModelSourceImage.builder()
                .showcaseId(showcaseId)
                .imageUrl(imageUrl)
                .angleType(angleType)
                .sortOrder(sortOrder)
                .createdAt(Instant.now())
                .build();
    }
}
