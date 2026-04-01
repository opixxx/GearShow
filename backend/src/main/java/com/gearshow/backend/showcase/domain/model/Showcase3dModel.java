package com.gearshow.backend.showcase.domain.model;

import com.gearshow.backend.showcase.domain.exception.InvalidShowcaseModelStatusTransitionException;
import com.gearshow.backend.showcase.domain.vo.ModelStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 쇼케이스 3D 모델 도메인 엔티티.
 *
 * <p>SHOWCASE Aggregate에 종속되며, 쇼케이스당 최대 1개만 존재한다.</p>
 */
@Getter
public class Showcase3dModel {

    private final Long id;
    private final Long showcaseId;
    private final String modelFileUrl;
    private final String previewImageUrl;
    private final ModelStatus modelStatus;
    private final String generationProvider;
    private final LocalDateTime requestedAt;
    private final LocalDateTime generatedAt;
    private final String failureReason;
    private final LocalDateTime createdAt;

    @Builder
    private Showcase3dModel(Long id, Long showcaseId, String modelFileUrl,
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

    /**
     * 3D 모델 생성을 요청한다.
     *
     * @param showcaseId         쇼케이스 ID
     * @param generationProvider 생성 제공자
     * @return 요청된 3D 모델
     */
    public static Showcase3dModel request(Long showcaseId, String generationProvider) {
        LocalDateTime now = LocalDateTime.now();
        return Showcase3dModel.builder()
                .showcaseId(showcaseId)
                .modelStatus(ModelStatus.REQUESTED)
                .generationProvider(generationProvider)
                .requestedAt(now)
                .createdAt(now)
                .build();
    }

    /**
     * 생성 진행 중 상태로 전환한다.
     *
     * @return 생성 중인 3D 모델
     */
    public Showcase3dModel startGenerating() {
        validateStatusTransition(ModelStatus.GENERATING);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.GENERATING)
                .generationProvider(this.generationProvider)
                .requestedAt(this.requestedAt)
                .createdAt(this.createdAt)
                .build();
    }

    /**
     * 생성 완료 처리한다.
     *
     * @param modelFileUrl    3D 모델 파일 URL
     * @param previewImageUrl 미리보기 이미지 URL
     * @return 완료된 3D 모델
     */
    public Showcase3dModel complete(String modelFileUrl, String previewImageUrl) {
        validateStatusTransition(ModelStatus.COMPLETED);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelFileUrl(modelFileUrl)
                .previewImageUrl(previewImageUrl)
                .modelStatus(ModelStatus.COMPLETED)
                .generationProvider(this.generationProvider)
                .requestedAt(this.requestedAt)
                .generatedAt(LocalDateTime.now())
                .createdAt(this.createdAt)
                .build();
    }

    /**
     * 생성 실패 처리한다.
     *
     * @param failureReason 실패 사유
     * @return 실패한 3D 모델
     */
    public Showcase3dModel fail(String failureReason) {
        validateStatusTransition(ModelStatus.FAILED);
        return Showcase3dModel.builder()
                .id(this.id)
                .showcaseId(this.showcaseId)
                .modelStatus(ModelStatus.FAILED)
                .generationProvider(this.generationProvider)
                .requestedAt(this.requestedAt)
                .failureReason(failureReason)
                .createdAt(this.createdAt)
                .build();
    }

    /**
     * 현재 생성 중인지 확인한다.
     *
     * @return 생성 중 여부
     */
    public boolean isGenerating() {
        return this.modelStatus == ModelStatus.GENERATING;
    }

    private void validateStatusTransition(ModelStatus target) {
        boolean valid = switch (this.modelStatus) {
            case REQUESTED -> target == ModelStatus.GENERATING;
            case GENERATING -> target == ModelStatus.COMPLETED || target == ModelStatus.FAILED;
            case FAILED -> target == ModelStatus.REQUESTED;
            case COMPLETED -> false;
        };

        if (!valid) {
            throw new InvalidShowcaseModelStatusTransitionException();
        }
    }
}
