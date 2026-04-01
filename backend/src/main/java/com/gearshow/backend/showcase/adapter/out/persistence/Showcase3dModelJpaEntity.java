package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 쇼케이스 3D 모델 JPA 엔티티.
 */
@Entity
@Table(name = "showcase_3d_model")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Showcase3dModelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showcase_3d_model_id")
    private Long id;

    @Column(name = "showcase_id", nullable = false, unique = true)
    private Long showcaseId;

    @Column(name = "model_file_url")
    private String modelFileUrl;

    @Column(name = "preview_image_url")
    private String previewImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_status", nullable = false)
    private ModelStatus modelStatus;

    @Column(name = "generation_provider")
    private String generationProvider;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Showcase3dModelJpaEntity(Long id, Long showcaseId, String modelFileUrl,
                                     String previewImageUrl, ModelStatus modelStatus,
                                     String generationProvider, LocalDateTime requestedAt,
                                     LocalDateTime generatedAt, String failureReason,
                                     LocalDateTime createdAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.modelFileUrl = modelFileUrl;
        this.previewImageUrl = previewImageUrl;
        this.modelStatus = modelStatus;
        this.generationProvider = generationProvider;
        this.requestedAt = requestedAt;
        this.generatedAt = generatedAt;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }
}
