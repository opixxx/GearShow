package com.gearshow.backend.showcase.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 쇼케이스 3D 모델 JPA 엔티티 — 완성품 스냅샷 (ADR-010).
 *
 * <p>프로세스 상태 컬럼은 모두 {@code model_generation_workflow} 로 이관됐다. 이 테이블의 행은
 * {@code DownloadFinalizer.finalizeDownload} 의 TX_final 에서 UPSERT 로 기록된다.</p>
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

    @Column(name = "model_file_url", nullable = false)
    private String modelFileUrl;

    @Column(name = "preview_image_url")
    private String previewImageUrl;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private Showcase3dModelJpaEntity(Long id, Long showcaseId, String modelFileUrl,
                                     String previewImageUrl, Instant generatedAt,
                                     Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.showcaseId = showcaseId;
        this.modelFileUrl = modelFileUrl;
        this.previewImageUrl = previewImageUrl;
        this.generatedAt = generatedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 기존 행을 최신 완성품 산출물로 덮어쓴다. Persistence Adapter UPSERT 경로 전용.
     */
    void replaceArtifact(String modelFileUrl, String previewImageUrl, Instant generatedAt, Instant updatedAt) {
        this.modelFileUrl = modelFileUrl;
        this.previewImageUrl = previewImageUrl;
        this.generatedAt = generatedAt;
        this.updatedAt = updatedAt;
    }
}
