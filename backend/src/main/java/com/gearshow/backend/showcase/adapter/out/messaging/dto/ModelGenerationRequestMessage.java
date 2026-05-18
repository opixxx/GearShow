package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

/**
 * 3D 모델 생성 요청 메시지.
 * Kafka 를 통해 워커에게 전달된다.
 *
 * <p><b>스키마 하위 호환</b>: {@code ignoreUnknown=true} 로 설정하여 Producer 가 새 필드를 추가해도
 * Consumer 가 역직렬화 실패하지 않는다. 또한 ADR-010 직후 cut over 단계에서 큐에 옛 스키마
 * (`showcase3dModelId` 포함) 가 남아있을 수 있는데, 같은 옵션 덕분에 Consumer 가 무시한다.</p>
 *
 * <p><b>messageId</b>: P1-B-γ 이후 {@code SHA-256(Idempotency-Key)} hex 64 자 (ADR-011 ③).
 * Consumer 의 {@code processed_message.event_id} 중복 차단과 Outbox {@code event_id} UNIQUE
 * 제약을 함께 만족시킨다. 단 입장 큐 drainer 재발행분(ADR-025)은 원본과 다른
 * {@code AdmissionDrainMessageId} 형식 id 를 쓴다 — 원본 id 재사용 시 park 시점
 * markProcessed 되어 Worker isProcessed 컷으로 silent skip 되기 때문(NN1).</p>
 *
 * <p><b>{@code bypassAdmission} (ADR-025)</b>: drainer 가 재발행한 메시지는 {@code true} —
 * Worker 가 admission 게이트 없는 {@code prepareFromAdmissionQueue} 로 라우팅한다(이미 큐에서
 * 선발됨, 재park score 리셋 방지). 일반 요청은 {@code false}. 구 스키마 메시지(필드 부재)는
 * primitive boolean 기본값 {@code false} 로 역직렬화되어 도입 전 동작과 동일하다(N2 호환).</p>
 *
 * @param messageId      멱등성 보장을 위한 결정적 식별자 (원본: SHA-256 hex 64자 / drain: NN1 형식)
 * @param workflowId     {@code model_generation_workflow} 행 ID (ADR-010)
 * @param showcaseId     쇼케이스 ID (파티션 키 + Outbox aggregate ID)
 * @param requestedAt    요청 시각
 * @param bypassAdmission drainer 재발행분이면 {@code true} (admission 게이트 우회, ADR-025)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelGenerationRequestMessage(
        String messageId,
        Long workflowId,
        Long showcaseId,
        Instant requestedAt,
        boolean bypassAdmission
) {

    public ModelGenerationRequestMessage {
        Objects.requireNonNull(messageId, "messageId는 필수입니다");
        Objects.requireNonNull(workflowId, "workflowId는 필수입니다");
        Objects.requireNonNull(showcaseId, "showcaseId는 필수입니다");
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다");
        // bypassAdmission 은 primitive — 구 JSON 에 필드가 없으면 Jackson 이 기본값 false 로 채운다.
    }

    /** 일반(최초) 요청. {@code bypassAdmission=false} — Worker 가 admission 게이트를 탄다. */
    public static ModelGenerationRequestMessage of(String messageId,
                                                   Long workflowId,
                                                   Long showcaseId) {
        return new ModelGenerationRequestMessage(
                messageId, workflowId, showcaseId, Instant.now(), false);
    }

    /**
     * 입장 큐 drainer 재발행분 (ADR-025). {@code bypassAdmission=true} — Worker 가 게이트 없는
     * {@code prepareFromAdmissionQueue} 로 라우팅한다. {@code messageId} 는 반드시
     * {@code AdmissionDrainMessageId} 형식(원본과 다름, NN1).
     */
    public static ModelGenerationRequestMessage ofDrain(String messageId,
                                                        Long workflowId,
                                                        Long showcaseId) {
        return new ModelGenerationRequestMessage(
                messageId, workflowId, showcaseId, Instant.now(), true);
    }
}
