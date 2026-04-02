package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 3D 모델 생성 워커.
 * Kafka에서 생성 요청 메시지를 소비하여 3D 모델을 생성하고 결과를 DB에 반영한다.
 *
 * <p>처리 흐름:
 * 1. Kafka에서 생성 요청 메시지 소비
 * 2. 모델 상태를 GENERATING으로 변경
 * 3. ModelGenerationClient(Fake)로 3D 모델 생성
 * 4. 결과에 따라 COMPLETED/FAILED로 상태 변경
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ModelGenerationClient modelGenerationClient;

    @KafkaListener(
            topics = KafkaConfig.MODEL_GENERATION_REQUEST_TOPIC,
            groupId = "model-generation-worker",
            containerFactory = "modelGenerationRequestListenerFactory"
    )
    public void processModelGeneration(ModelGenerationRequestMessage message) {
        log.info("3D 모델 생성 요청 수신 - showcase3dModelId: {}, showcaseId: {}",
                message.showcase3dModelId(), message.showcaseId());

        Showcase3dModel model = findAndStartGenerating(message.showcase3dModelId());
        if (model == null) {
            return;
        }

        generateAndUpdateResult(model, message.showcaseId());
    }

    /**
     * 모델을 조회하고 GENERATING 상태로 전환한다.
     */
    private Showcase3dModel findAndStartGenerating(Long showcase3dModelId) {
        Showcase3dModel model = showcase3dModelPort.findById(showcase3dModelId)
                .orElse(null);

        if (model == null) {
            log.warn("3D 모델을 찾을 수 없습니다 - showcase3dModelId: {}", showcase3dModelId);
            return null;
        }

        Showcase3dModel generating = model.startGenerating();
        return showcase3dModelPort.save(generating);
    }

    /**
     * 3D 모델을 생성하고 결과를 DB에 반영한다.
     */
    private void generateAndUpdateResult(Showcase3dModel model, Long showcaseId) {
        GenerationResult result = modelGenerationClient.generate(model.getId(), showcaseId);

        if (result.success()) {
            Showcase3dModel completed = model.complete(
                    result.modelFileUrl(), result.previewImageUrl());
            showcase3dModelPort.save(completed);
            log.info("3D 모델 생성 완료 - showcase3dModelId: {}", model.getId());
        } else {
            Showcase3dModel failed = model.fail(result.failureReason());
            showcase3dModelPort.save(failed);
            log.warn("3D 모델 생성 실패 - showcase3dModelId: {}, 사유: {}",
                    model.getId(), result.failureReason());
        }
    }
}
