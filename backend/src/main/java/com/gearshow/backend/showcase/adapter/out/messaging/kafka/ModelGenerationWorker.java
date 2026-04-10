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
 * <p><b>폴링 분리 아키텍처</b>: Worker 는 Tripo task 생성까지만 수행하고 즉시 반환한다
 * (수 초 이내). Tripo 폴링과 결과 다운로드는 별도 스케줄러가 담당한다.</p>
 *
 * <p><b>멱등성</b>: 유니크 키 테이블({@code processed_message}) 기반.
 * 한 번 tryAcquire 에 성공한 메시지는 <b>재시도하지 않는다</b> (release 호출 없음).
 * 처리 중 예외가 발생해도 멱등성 레코드는 유지되어 중복 Tripo 호출을 차단한다.</p>
 *
 * <p>비즈니스 실패 시 {@link PrepareModelGenerationUseCase} 내부에서 모델 상태를
 * FAILED 또는 UNAVAILABLE 로 전환하고 정상 반환한다. 예외가 Worker 까지 올라오는 경우는
 * 정말 예측하지 못한 인프라 장애 뿐이며, 이 경우 Spring Kafka 의 DefaultErrorHandler 가
 * 짧은 재시도 후 DLT 로 메시지를 이동시킨다 (Phase 6 에서 설정).</p>
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

        // 비즈니스 실패는 UseCase 내부에서 모델 상태 전환으로 처리된다.
        // release() 를 호출하지 않는 이유:
        // 1. release → 재시도 → 같은 modelId 에 대해 Tripo 를 또 호출 → $0.6 중복 과금
        // 2. 멱등성 보장이 release 로 무너지는 원인이었음 (Phase 2 에서 제거)
        prepareModelGenerationUseCase.prepare(
                message.showcase3dModelId(), message.showcaseId());
    }
}
