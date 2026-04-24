package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.dto.ModelGenerationResult;
import com.gearshow.backend.showcase.application.dto.ShowcaseModelStatus;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationEventPublisher;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.ModelSourceImagePort;
import com.gearshow.backend.showcase.domain.model.ModelSourceImage;
import com.gearshow.backend.showcase.domain.vo.AngleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 3D 모델 생성 요청 트랜잭션 서비스 (ADR-010 프로세스 분리 반영).
 *
 * <p>Showcase3dModel 은 완성품 전용이므로 요청 시점에는 {@code model_generation_workflow} 와
 * {@code model_source_image} 만 INSERT 된다. Outbox 발행은 같은 TX 에서 수행되어
 * Transactional Outbox 불변식을 유지한다.</p>
 */
@Service
@RequiredArgsConstructor
public class RequestModelGenerationService {

    private static final AngleType[] ANGLE_TYPES = AngleType.values();

    private final ModelSourceImagePort modelSourceImagePort;
    private final ModelGenerationWorkflowPort modelGenerationWorkflowPort;
    private final ModelGenerationEventPublisher modelGenerationEventPublisher;

    /**
     * 신규 요청: 소스 이미지 + workflow {@code attempt_no=1} + Outbox 이벤트를 한 TX 에 기록한다.
     */
    @Transactional
    public ModelGenerationResult saveSourceImagesAndRequest(Long showcaseId,
                                                            String idempotencyKey,
                                                            List<String> imageUrls) {
        saveSourceImages(showcaseId, imageUrls);
        long workflowId = modelGenerationWorkflowPort.saveRequested(showcaseId, idempotencyKey, 1);
        modelGenerationEventPublisher.publishRequested(
                workflowId, showcaseId, showcaseId, idempotencyKey);
        return new ModelGenerationResult(workflowId, ShowcaseModelStatus.GENERATING);
    }

    /**
     * 재시도: 기존 소스 이미지가 있다면 대체하고, workflow {@code attempt_no} 를 증가시켜 새 행 INSERT.
     * 이전 attempt 의 {@code failure_*} 는 보존된다 (ADR-010 이력 보존 원칙).
     */
    @Transactional
    public ModelGenerationResult resetSourceImagesAndRequestRetry(Long showcaseId,
                                                                  String idempotencyKey,
                                                                  List<String> imageUrls) {
        saveSourceImages(showcaseId, imageUrls);
        int attemptNo = modelGenerationWorkflowPort.nextAttemptNo(showcaseId);
        long workflowId = modelGenerationWorkflowPort.saveRequested(
                showcaseId, idempotencyKey, attemptNo);
        modelGenerationEventPublisher.publishRequested(
                workflowId, showcaseId, showcaseId, idempotencyKey);
        return new ModelGenerationResult(workflowId, ShowcaseModelStatus.GENERATING);
    }

    private void saveSourceImages(Long showcaseId, List<String> imageUrls) {
        List<ModelSourceImage> sourceImages = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            AngleType angleType = ANGLE_TYPES[i % ANGLE_TYPES.length];
            sourceImages.add(ModelSourceImage.create(
                    showcaseId, imageUrls.get(i), angleType, i + 1));
        }
        modelSourceImagePort.saveAll(sourceImages);
    }
}
