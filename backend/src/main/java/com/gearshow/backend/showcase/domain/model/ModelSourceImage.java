package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.vo.AngleType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 3D 모델 소스 이미지 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 3D 모델 생성에 필요한 원본 이미지이다.
 * 최소 4장(앞/뒤/좌/우)이 필요하다.</p>
 */
@Getter
public class ModelSourceImage {

    private final Long id;
    private final Long showcase3dModelId;
    private final String imageUrl;
    private final AngleType angleType;
    private final int sortOrder;
    private final Instant createdAt;

    @Builder
    private ModelSourceImage(Long id, Long showcase3dModelId, String imageUrl,
                             AngleType angleType, int sortOrder,
                             Instant createdAt) {
        this.id = id;
        this.showcase3dModelId = showcase3dModelId;
        this.imageUrl = imageUrl;
        this.angleType = angleType;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }

    /**
     * 새로운 소스 이미지를 생성한다.
     *
     * @param showcase3dModelId 3D 모델 ID
     * @param imageUrl          이미지 URL
     * @param angleType         촬영 각도
     * @param sortOrder         정렬 순서
     * @return 생성된 소스 이미지
     */
    public static ModelSourceImage create(Long showcase3dModelId, String imageUrl,
                                          AngleType angleType, int sortOrder) {
        return ModelSourceImage.builder()
                .showcase3dModelId(showcase3dModelId)
                .imageUrl(imageUrl)
                .angleType(angleType)
                .sortOrder(sortOrder)
                .createdAt(Instant.now())
                .build();
    }
}
