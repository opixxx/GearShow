package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.AngleType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 3D 모델 소스 이미지 JPA 엔티티.
 */
@Entity
@Table(name = "model_source_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelSourceImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_source_image_id")
    private Long id;

    @Column(name = "showcase_3d_model_id", nullable = false)
    private Long showcase3dModelId;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "angle_type", nullable = false)
    private AngleType angleType;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private ModelSourceImageJpaEntity(Long id, Long showcase3dModelId, String imageUrl,
                                      AngleType angleType, int sortOrder,
                                      Instant createdAt) {
        this.id = id;
        this.showcase3dModelId = showcase3dModelId;
        this.imageUrl = imageUrl;
        this.angleType = angleType;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }
}
