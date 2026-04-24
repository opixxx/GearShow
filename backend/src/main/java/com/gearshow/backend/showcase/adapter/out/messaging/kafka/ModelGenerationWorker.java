package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.infrastructure.config.ShowcaseKafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 3D 모델 생성 Kafka Consumer 어댑터.
 *
 * <p><b>P1-D-α+β 재설계 (본 PR)</b>: workflow 테이블 기반으로 동작한다. 메시지의
 * {@code workflowId} 를 기반으로 {@link PrepareWorkflowUseCase#prepare(Long)} 를 호출해
 * TX1 ({@code REQUESTED → PREPARING}) 과 S3 존재 검증을 수행한다. Tripo 호출·TX2 는
 * 후속 P1-D-γ 에서 UseCase 내부로 연결된다.</p>
 *
 * <p><b>설계 결정 #5 (멱등성 레코드 release 규칙)</b>:</p>
 * <ul>
 *   <li>tryAcquire 후 <b>Tripo 호출 전</b> 실패: release() 호출 → Spring Kafka 재시도 가능</li>
 *   <li>tryAcquire 후 <b>Tripo 호출 후</b> 실패: release 금지 → 이중 과금 방지</li>
 * </ul>
 *
 * <p>본 PR 범위에선 Tripo 호출이 아직 없으므로 예외 전파 시 항상 release() 호출이 안전하다.
 * P1-D-γ 에서 Tripo 호출 후 실패 분기를 추가할 때 이 Javadoc 과 catch 로직을 함께 재정의한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final PrepareWorkflowUseCase prepareWorkflowUseCase;
    private final AcquireIdempotencyUseCase acquireIdempotencyUseCase;

    @KafkaListener(
            topics = ShowcaseKafkaTopicConfig.MODEL_GENERATION_REQUEST_TOPIC,
            groupId = "model-generation-worker",
            containerFactory = "modelGenerationRequestListenerFactory"
    )
    public void processModelGeneration(ModelGenerationRequestMessage message) {
        // 방어: record 컴팩트 생성자가 non-null 을 보장하지만, 역직렬화 경로 오류 대비.
        if (message.workflowId() == null) {
            log.error("workflowId 누락 메시지 수신 — 무시 - messageId: {}", message.messageId());
            return;
        }

        // 멱등성 체크: 이미 처리된 메시지면 즉시 무시 (ADR-011 ③).
        if (!acquireIdempotencyUseCase.tryAcquire(
                message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION)) {
            return;
        }

        log.info("3D 모델 생성 요청 수신 - messageId: {}, workflowId: {}, showcaseId: {}",
                message.messageId(), message.workflowId(), message.showcaseId());

        try {
            prepareWorkflowUseCase.prepare(message.workflowId());
        } catch (Exception e) {
            // [설계 결정 #5] 예외가 여기까지 전파됐다면 Tripo 호출 전 실패 (본 PR 범위 내에선 항상 성립).
            // release() 로 멱등성 레코드를 해제하여 Spring Kafka 재시도를 허용한다.
            log.warn("TX1 실패 - 멱등성 레코드 release - messageId: {}, workflowId: {}",
                    message.messageId(), message.workflowId());
            try {
                acquireIdempotencyUseCase.release(
                        message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
            } catch (Exception releaseEx) {
                // release 실패해도 원본 예외를 전파한다. P1-G Reconcile 이 최종 안전망.
                log.error("멱등성 레코드 release 실패 - Reconcile 이 최종 복구 예정 - messageId: {}",
                        message.messageId(), releaseEx);
            }
            throw e; // Spring Kafka DefaultErrorHandler 가 재시도 → DLT
        }
    }
}
