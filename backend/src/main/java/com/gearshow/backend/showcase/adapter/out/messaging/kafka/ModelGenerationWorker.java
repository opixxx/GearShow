package com.gearshow.backend.showcase.adapter.out.messaging.kafka;

import com.gearshow.backend.platform.idempotency.application.port.in.MessageIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;
import com.gearshow.backend.showcase.adapter.out.messaging.AdmissionDrainMessageId;
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
 * 의 복구 대기로도 동작한다. {@code include} 에 명시되지 않은 RuntimeException 은 재시도 없이
 * 즉시 DLT 로 라우팅 — 프로그래밍 버그/스키마 오류는 빠르게 알람으로 전환한다.</p>
 *
 * <p><b>DLT 핸들러 ({@link DltHandler})</b>: 재시도 소진 후 DLT 도달 시 workflow 를 FAILED 로
 * 마킹하고 ALERT 로그를 남긴다. {@code tripo_pending_task} 정리는 Reconcile(P1-G) 의
 * {@code recoverPreparing} 책임. Tripo cancel 은 API 미지원이라 어떤 경로에서도 호출하지 않는다
 * (ADR-011 v1.1 §④).</p>
 *
 * <p><b>멱등성 INSERT 시점 (ADR-011 §2.③ 양보 불가 규칙)</b>: {@code processed_message} 는
 * 메시지 처리 *정상 종료 후* INSERT 한다 (완료 시점 INSERT). UseCase 가 예외를 던지면
 * markProcessed 를 호출하지 않은 상태로 RetryableTopic 재시도가 진행되어 다음 시도에서 자연스럽게
 * 재진입한다. 동시성 1회 보장은 워크플로우 단계의 조건부 UPDATE (ADR-012) 가 책임지므로,
 * 메시지 멱등성 테이블은 브로커 재전송 시 빠른 컷 역할만 수행한다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ModelGenerationWorker {

    private final PrepareWorkflowUseCase prepareWorkflowUseCase;
    private final MessageIdempotencyUseCase messageIdempotencyUseCase;
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

        // 재전송된 이미 처리 완료 메시지면 즉시 무시 (ADR-011 §2.③ 빠른 컷).
        if (messageIdempotencyUseCase.isProcessed(
                message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION)) {
            log.debug("이미 처리 완료된 메시지 — skip. messageId: {}, workflowId: {}",
                    message.messageId(), message.workflowId());
            return;
        }

        log.info("3D 모델 생성 요청 수신 - messageId: {}, workflowId: {}, showcaseId: {}",
                message.messageId(), message.workflowId(), message.showcaseId());

        // 동시성 1회 보장은 워크플로우 단계의 조건부 UPDATE (ADR-012) 가 책임진다.
        // 예외가 던져지면 markProcessed 미호출 → @RetryableTopic 이 retry 토픽으로 라우팅 →
        // 다음 시도에서 isProcessed=false 로 자연스럽게 재진입한다.
        boolean routedToAdmissionQueue = false;
        if (message.bypassAdmission()) {
            // 입장 큐 drainer 재발행분 (ADR-025): admission 게이트 미경유. epoch 은
            // in-flight latency(NN7) 측정용. drain messageId 형식이 깨지면 게이트 우회를
            // 허용하지 않고 게이트 경유 prepare() 로 폴백한다 — bypass=true 라도 무결성이
            // 깨진 입력은 신뢰하지 않는다(CodeRabbit, 게이트 우회 차단).
            try {
                long dispatchEpochMillis =
                        AdmissionDrainMessageId.parseDispatchEpoch(message.messageId());
                prepareWorkflowUseCase.prepareFromAdmissionQueue(
                        message.workflowId(), dispatchEpochMillis);
                routedToAdmissionQueue = true;
            } catch (IllegalArgumentException e) {
                log.warn("bypassAdmission=true 이나 drain messageId 형식 불일치 — "
                                + "게이트 경유 prepare 로 폴백(게이트 우회 차단). messageId: {}",
                        message.messageId());
            }
        }
        if (!routedToAdmissionQueue) {
            prepareWorkflowUseCase.prepare(message.workflowId());
        }

        // 정상 종료 시점에 처리 완료 사실을 영구 기록 (ADR-011 §2 양보 불가 규칙).
        messageIdempotencyUseCase.markProcessed(
                message.messageId(), IdempotencyDomain.SHOWCASE_MODEL_GENERATION);
    }

    /**
     * 재시도가 모두 소진되어 DLT 도달한 메시지를 처리한다. workflow 를 FAILED 로 마킹하고
     * ALERT 로그를 남긴다. {@code tripo_pending_task} 정리는 Reconcile(P1-G) 의 책임이므로
     * 이 핸들러는 건드리지 않는다. Tripo cancel 은 API 미지원이라 어떤 경로에서도 호출하지 않으며,
     * stranded task 발생 시 1회분 크레딧 손실은 운영 측 일일 balance 모니터링으로 인지한다
     * (ADR-011 v1.1 §④).
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
