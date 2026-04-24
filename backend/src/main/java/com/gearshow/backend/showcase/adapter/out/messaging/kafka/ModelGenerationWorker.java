package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.AcquireIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.dto.ModelGenerationRequestMessage;
import com.gearshow.backend.showcase.application.dto.WorkflowFailureCode;
import com.gearshow.backend.showcase.application.exception.ModelGenerationRetryableException;
import com.gearshow.backend.showcase.application.port.in.PrepareWorkflowUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.infrastructure.config.ShowcaseKafkaTopicConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * 3D 모델 생성 Kafka Consumer 어댑터.
 *
 * <p><b>메시지 흐름 (P1-D-γ+δ)</b>: 메인 토픽({@code showcase.model-generation.request}) 에서
 * 메시지를 수신해 {@link PrepareWorkflowUseCase#prepare(Long)} 에 위임한다. 이 UseCase 는
 * 내부에서 TX1 → Tripo 호출 → tripo_pending_task 선저장 → TX2 전체를 처리한다.</p>
 *
 * <p><b>재시도 전략 ({@link RetryableTopic})</b>: Retryable/CircuitOpen 예외는 retry 토픽
 * ({@code showcase.model-generation.request-retry}) 으로 이동해 exp backoff 후 재처리된다.
 * attempts=5, 30s → 60s → 120s → 240s → 480s → DLT. 총 ~15분 재시도 윈도우가 Circuit Breaker
 * 의 복구 대기로도 동작한다.</p>
 *
 * <p><b>DLT 핸들러 ({@link DltHandler})</b>: 재시도 소진 후 DLT 도달 시 workflow 를 FAILED 로
 * 마킹하고 ALERT 로그를 남긴다. {@code tripo_pending_task} 정리 · Tripo cancel 은 Reconcile(P1-G)
 * 범위.</p>
 *
 * <p><b>설계 결정 #5 (멱등성 레코드 release 규칙)</b>:</p>
 * <ul>
 *   <li>UseCase 가 예외를 던지면 → Tripo 호출 <b>전</b> 실패로 간주 → release() 호출 후 rethrow</li>
 *   <li>UseCase 가 정상 반환하면 → Tripo 호출 후 처리는 UseCase 내부에서 완료 (release 없음)</li>
 * </ul>
 * 이유: Tripo 호출 성공 후 실패는 UseCase 가 내부에서 흡수해 정상 반환하므로 예외가 Worker 까지
 * 올라오지 않는다 (PrepareWorkflowService.transitionToGeneratingUnderLock 의 catch 블록 참조).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final PrepareWorkflowUseCase prepareWorkflowUseCase;
    private final AcquireIdempotencyUseCase acquireIdempotencyUseCase;
    private final ModelGenerationWorkflowPort workflowPort;

    @RetryableTopic(
            attempts = "5",
            backoff = @Backoff(delay = 30_000, multiplier = 2, maxDelay = 480_000),
            include = {
                    CallNotPermittedException.class,
                    ModelGenerationRetryableException.class
            },
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            retryTopicSuffix = "-retry",
            dltTopicSuffix = ".DLT",
            // KafkaProducerConfig 의 메서드 이름이 Bean 이름 ("kafkaTemplate").
            // byte[] 를 쓰는 OutboxKafkaConfig 의 kafkaTemplate 와 공존하므로 명시 필요.
            kafkaTemplate = "kafkaTemplate"
    )
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
            // [설계 결정 #5] 예외 전파 = Tripo 호출 전 실패 (UseCase 가 Tripo 호출 후 실패는 내부 흡수).
            // release() 로 멱등성 레코드를 해제하여 Spring Kafka retry 토픽 재시도를 허용한다.
            log.warn("UseCase 실패 (Tripo 호출 전) - 멱등성 레코드 release - "
                            + "messageId: {}, workflowId: {}",
                    message.messageId(), message.workflowId());
            try {
                acquireIdempotencyUseCase.release(
                        message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
            } catch (Exception releaseEx) {
                // release 실패해도 원본 예외를 전파한다. Reconcile(P1-G) 이 최종 안전망.
                log.error("멱등성 레코드 release 실패 - Reconcile 이 최종 복구 예정 - messageId: {}",
                        message.messageId(), releaseEx);
            }
            throw e; // @RetryableTopic 이 retry → DLT 라우팅
        }
    }

    /**
     * 재시도가 모두 소진되어 DLT 도달한 메시지를 처리한다. workflow 를 FAILED 로 마킹하고
     * ALERT 로그를 남긴다. {@code tripo_pending_task} 정리와 Tripo cancel 은 Reconcile(P1-G)
     * 의 책임이므로 이 핸들러는 건드리지 않는다.
     */
    @DltHandler
    public void handleDlt(ModelGenerationRequestMessage message) {
        log.error("ALERT: DLT 도달 - 재시도 소진. workflowId: {}, messageId: {}",
                message.workflowId(), message.messageId());
        if (message.workflowId() == null) {
            return;
        }
        int affected = workflowPort.markFailed(
                message.workflowId(),
                WorkflowFailureCode.TRIPO_RETRY_EXHAUSTED,
                "Tripo 재시도 5회 소진 (DLT 도달)",
                "TRIPO_API");
        if (affected == 0) {
            log.warn("DLT markFailed affected=0 — 이미 종결된 워크플로우. workflowId: {}",
                    message.workflowId());
        }
    }
}
