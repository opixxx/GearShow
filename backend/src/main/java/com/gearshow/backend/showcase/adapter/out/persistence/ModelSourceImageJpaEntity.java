package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.AngleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 3D 모델 소스 이미지 JPA 엔티티.
 *
 * <p><b>ADR-010 변경</b>: {@code showcase_3d_model_id} FK → {@code showcase_id} 로 대체.
 * Showcase3dModel 이 완성품 전용으로 축소됐기 때문에 요청 시점에 존재하지 않는 Showcase3dModel
 * id 를 참조할 수 없다.</p>
 */
@Entity
@Table(
        name = "model_source_image",
        indexes = {
                @Index(name = "idx_msi_showcase_sort", columnList = "showcase_id, sort_order")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelSourceImageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_source_image_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false)
    private Long showcaseId;

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
    private ModelSourceImageJpaEntity(Long id, Long showcaseId, String imageUrl,
                                      AngleType angleType, int sortOrder,
                                      Instant createdAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.imageUrl = imageUrl;
        this.angleType = angleType;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
    }
}
