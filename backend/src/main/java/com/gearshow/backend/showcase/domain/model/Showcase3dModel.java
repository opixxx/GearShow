package com.gearshow.backend.showcase.domain.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 쇼케이스 3D 모델 — 완성품 스냅샷 (ADR-010).
 *
 * <p>"완성된 3D 모델" 이라는 도메인 개념만 담는다. 생성 파이프라인의 진행 상태
 * (REQUESTED · PREPARING · GENERATING) 는 프로세스 테이블 {@code model_generation_workflow}
 * 가 담당한다. 따라서 이 Aggregate 의 행은 {@code DownloadFinalizer.finalizeDownload} 가
 * 완료(TX_final) 시점에 최초 생성/갱신되며, 재시도 중간 상태를 이 테이블로 노출하지 않는다.</p>
 *
 * <p><b>ADR-010 양보 불가 규칙</b>:</p>
 * <ul>
 *   <li>프로세스 상태 컬럼 0개 — status · retry_count · failure_* · last_polled_at 등은 모두
 *       {@code model_generation_workflow} 쪽에만 존재.</li>
 *   <li>쇼케이스당 3D 모델 하나 — {@code showcase_id} UNIQUE.</li>
 * </ul>
 */
@Getter
public class Showcase3dModel {

    private final Long id;
    private final Long showcaseId;
    private final String modelFileUrl;
    private final String previewImageUrl;
    private final Instant generatedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    @Builder
    private Showcase3dModel(Long id, Long showcaseId, String modelFileUrl,
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
     * 완성품 3D 모델 신규 생성 팩토리. {@code DownloadFinalizer.finalizeDownload} 의
     * TX_final 에서 UPSERT 경로로 사용된다.
     *
     * @param showcaseId      완성된 3D 모델이 귀속되는 쇼케이스 ID
     * @param modelFileUrl    S3 미러링 완료된 {@code .glb} 파일 URL
     * @param previewImageUrl Tripo 가 제공한 미리보기 이미지 URL
     */
    public static Showcase3dModel create(Long showcaseId, String modelFileUrl, String previewImageUrl) {
        Instant now = Instant.now();
        return Showcase3dModel.builder()
                .showcaseId(showcaseId)
                .modelFileUrl(modelFileUrl)
                .previewImageUrl(previewImageUrl)
                .generatedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

}
