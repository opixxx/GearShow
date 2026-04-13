package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.PrepareModelGenerationUseCase;
import com.gearshow.backend.showcase.infrastructure.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 3D 모델 생성 Kafka Consumer 어댑터.
 *
 * <p><b>설계 결정 #5 (멱등성 레코드 release 규칙)</b>:</p>
 * <ul>
 *   <li>tryAcquire 후 <b>Tripo 호출 전</b> 실패: release() 호출 → Spring Kafka 재시도 가능</li>
 *   <li>tryAcquire 후 <b>Tripo 호출 후</b> 실패: release 금지 → 이중 과금 방지</li>
 * </ul>
 *
 * <p>PrepareModelGenerationUseCase 가 예외를 던지면 Tripo 호출 전 실패로 간주하고
 * release() 를 호출한다. Tripo 호출 후 실패는 UseCase 내부에서 모델 상태를 전환하고
 * 정상 반환하므로 예외가 Worker 까지 올라오지 않는다.</p>
 *
 * <p>이 규칙의 근거: PrepareModelGenerationService 에서 Tripo 호출 성공 후 DB 실패 시
 * orphan 마킹을 하고 정상 반환한다. 예외가 Worker 까지 전파되는 경우는 반드시
 * Tripo 호출 전 단계(PREPARING 전환, S3 다운로드 등)의 실패다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final PrepareModelGenerationUseCase prepareModelGenerationUseCase;
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
            prepareModelGenerationUseCase.prepare(
                    message.showcase3dModelId(), message.showcaseId());
        } catch (Exception e) {
            // [설계 결정 #5] 예외가 여기까지 전파됐다면 Tripo 호출 전 실패.
            // (Tripo 호출 후 실패는 UseCase 내부에서 처리하고 정상 반환함.)
            // release() 로 멱등성 레코드를 해제하여 Spring Kafka 재시도를 허용한다.
            // 과금 전이므로 재시도해도 이중 과금 위험 없음.
            log.warn("Tripo 호출 전 실패 - 멱등성 레코드 release - messageId: {}, showcase3dModelId: {}",
                    message.messageId(), message.showcase3dModelId());
            try {
                acquireIdempotencyUseCase.release(
                        message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
            } catch (Exception releaseEx) {
                // release 실패해도 원본 예외를 전파해야 한다.
                // release 실패하면 멱등성 레코드가 남아 DLT 봉쇄가 발생하지만,
                // Recovery 스케줄러가 PREPARING + 5분 초과로 최종 복구한다 (설계 결정 #5 보험).
                log.error("멱등성 레코드 release 실패 - Recovery 가 최종 복구 예정 - messageId: {}",
                        message.messageId(), releaseEx);
            }
            throw e; // Spring Kafka DefaultErrorHandler 가 재시도 → DLT
        }
    }
}
