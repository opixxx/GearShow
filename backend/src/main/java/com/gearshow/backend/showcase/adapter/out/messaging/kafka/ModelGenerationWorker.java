package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationClient.GenerationResult;
import com.gearshow.backend.showcase.application.port.out.Showcase3dModelPort;
import com.gearshow.backend.showcase.application.port.out.ShowcasePort;
import com.gearshow.backend.showcase.domain.model.Showcase3dModel;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * 3D 모델 생성 워커.
 * Kafka에서 생성 요청 메시지를 소비하여 3D 모델을 생성하고 결과를 DB에 반영한다.
 *
 * <p>처리 흐름:
 * 1. 멱등성 체크 (이미 처리됐으면 무시)
 * 2. 모델 조회 + GENERATING 상태로 전환
 * 3. ModelGenerationClient로 3D 모델 생성
 * 4. 결과에 따라 COMPLETED/FAILED로 상태 변경
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final Showcase3dModelPort showcase3dModelPort;
    private final ShowcasePort showcasePort;
    private final ModelGenerationClient modelGenerationClient;
    private final AcquireIdempotencyUseCase acquireIdempotencyUseCase;

    @KafkaListener(
            topics = KafkaConfig.MODEL_GENERATION_REQUEST_TOPIC,
            groupId = "model-generation-worker",
            containerFactory = "modelGenerationRequestListenerFactory"
    )
    public void processModelGeneration(ModelGenerationRequestMessage message) {
        // 멱등성 체크: 이미 처리된 메시지면 즉시 무시 (리밸런싱 / 재전달 방어)
        if (!acquireIdempotencyUseCase.tryAcquire(
                message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION)) {
            return;
        }

        log.info("3D 모델 생성 요청 수신 - messageId: {}, showcase3dModelId: {}, showcaseId: {}",
                message.messageId(), message.showcase3dModelId(), message.showcaseId());

        showcase3dModelPort.findById(message.showcase3dModelId())
                .ifPresentOrElse(
                        model -> generateAndUpdateResult(startGenerating(model), message.showcaseId()),
                        () -> log.warn("3D 모델을 찾을 수 없습니다 - showcase3dModelId: {}",
                                message.showcase3dModelId())
                );
    }

    /**
     * 모델을 GENERATING 상태로 전환하고 저장한다.
     */
    private Showcase3dModel startGenerating(Showcase3dModel model) {
        return showcase3dModelPort.save(model.startGenerating());
    }

    /**
     * 3D 모델 생성 외부 호출을 수행하고 결과를 DB에 반영한다.
     * 외부 클라이언트 예외만 좁게 잡아 FAILED로 전환한다.
     */
    private void generateAndUpdateResult(Showcase3dModel model, Long showcaseId) {
        GenerationResult result;
        try {
            result = modelGenerationClient.generate(model.getId(), showcaseId);
        } catch (RestClientException | DataAccessException e) {
            markAsFailed(model, showcaseId, "외부 호출 또는 저장 실패");
            log.error("3D 모델 생성 외부 호출 실패 - showcase3dModelId: {}", model.getId(), e);
            return;
        }

        if (result.success()) {
            Showcase3dModel completed = model.complete(result.modelFileUrl(), result.previewImageUrl());
            showcase3dModelPort.save(completed);
            syncHas3dModel(showcaseId, true);
            log.info("3D 모델 생성 완료 - showcase3dModelId: {}", model.getId());
        } else {
            markAsFailed(model, showcaseId, result.failureReason());
            log.warn("3D 모델 생성 실패 - showcase3dModelId: {}, 사유: {}",
                    model.getId(), result.failureReason());
        }
    }

    /**
     * 모델을 FAILED 상태로 전환하고 저장한다.
     */
    private void markAsFailed(Showcase3dModel model, Long showcaseId, String reason) {
        Showcase3dModel failed = model.fail(reason);
        showcase3dModelPort.save(failed);
        syncHas3dModel(showcaseId, false);
    }

    /**
     * 쇼케이스의 has3dModel 플래그를 동기화한다.
     * 단일 UPDATE 쿼리로 다른 필드를 건드리지 않아 lost update를 방지한다.
     */
    private void syncHas3dModel(Long showcaseId, boolean has3dModel) {
        showcasePort.updateHas3dModel(showcaseId, has3dModel);
    }
}
