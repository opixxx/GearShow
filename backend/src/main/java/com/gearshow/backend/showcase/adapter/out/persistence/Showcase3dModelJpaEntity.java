package com.gearshow.backend.showcase.adapter.out.persistence;

import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    /**
     * Tripo task_id. Worker 가 createTask 성공 후 저장하며,
     * 폴링 스케줄러가 이 값으로 Tripo 상태를 조회한다.
     */
    @Column(name = "generation_task_id", length = 100)
    private String generationTaskId;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    /**
     * 폴링 스케줄러가 마지막으로 Tripo 상태를 확인한 시각.
     * stuck 감지(예: 15분 이상 미폴링)의 기준값이다.
     */
    @Column(name = "last_polled_at")
    private Instant lastPolledAt;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private Showcase3dModelJpaEntity(Long id, Long showcaseId, String modelFileUrl,
                                     String previewImageUrl, ModelStatus modelStatus,
                                     String generationProvider, String generationTaskId,
                                     Instant requestedAt, Instant generatedAt,
                                     Instant lastPolledAt, String failureReason,
                                     Instant createdAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.modelFileUrl = modelFileUrl;
        this.previewImageUrl = previewImageUrl;
        this.modelStatus = modelStatus;
        this.generationProvider = generationProvider;
        this.generationTaskId = generationTaskId;
        this.requestedAt = requestedAt;
        this.generatedAt = generatedAt;
        this.lastPolledAt = lastPolledAt;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
    }
}
