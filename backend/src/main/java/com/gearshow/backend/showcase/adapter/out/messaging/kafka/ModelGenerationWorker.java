package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.ProcessModelGenerationUseCase;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 3D 모델 생성 Kafka Consumer 어댑터.
 *
 * <p>책임은 다음 세 가지로 한정된다:</p>
 * <ol>
 *     <li>Kafka 메시지 수신 (트리거)</li>
 *     <li>멱등성 가드 적용 (리밸런싱 / 재전달 방어)</li>
 *     <li>비즈니스 처리 실패 시 멱등성 보상 삭제 (좀비 메시지 방지)</li>
 * </ol>
 *
 * <p>실제 비즈니스 로직(모델 조회/상태 전환/외부 호출/결과 저장)은
 * {@link ProcessModelGenerationUseCase}에 위임한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final ProcessModelGenerationUseCase processModelGenerationUseCase;
    private final AcquireIdempotencyUseCase acquireIdempotencyUseCase;

    @KafkaListener(
            topics = KafkaConfig.MODEL_GENERATION_REQUEST_TOPIC,
            groupId = "model-generation-worker",
            containerFactory = "modelGenerationRequestListenerFactory"
    )
    public void processModelGeneration(ModelGenerationRequestMessage message) {
        // 멱등성 체크: 이미 처리된 메시지면 즉시 무시
        if (!acquireIdempotencyUseCase.tryAcquire(
                message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION)) {
            return;
        }

        log.info("3D 모델 생성 요청 수신 - messageId: {}, showcase3dModelId: {}, showcaseId: {}",
                message.messageId(), message.showcase3dModelId(), message.showcaseId());

        try {
            processModelGenerationUseCase.process(message.showcase3dModelId(), message.showcaseId());
        } catch (RuntimeException e) {
            // 비즈니스 처리 실패 시 멱등성 선점을 되돌려 다음 재전달 시 재처리 가능하게 한다
            // (좀비 메시지 방지)
            acquireIdempotencyUseCase.release(
                    message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
            log.error("3D 모델 처리 실패로 멱등성 선점 해제 - messageId: {}, showcase3dModelId: {}",
                    message.messageId(), message.showcase3dModelId(), e);
            throw e;
        }
    }
}
