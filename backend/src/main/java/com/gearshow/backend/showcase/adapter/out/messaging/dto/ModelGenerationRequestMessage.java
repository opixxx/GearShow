package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

/**
 * 3D 모델 생성 요청 메시지.
 * Kafka를 통해 워커에게 전달된다.
 *
 * <p><b>설계 결정 #6 (메시지 스키마 하위 호환)</b>:
 * {@code ignoreUnknown=true} 로 설정하여 Producer 가 새 필드를 추가해도
 * Consumer 가 역직렬화 실패하지 않게 한다. 서버 분리 후 배포 시점 차이에 의한
 * poison pill 문제를 방지한다.</p>
 *
 * <p><b>P1-B-γ 변경</b>: {@code messageId} 는 더 이상 UUID 가 아니라
 * {@code SHA-256(Idempotency-Key)} hex 64자다 (ADR-011 ③). Consumer 의
 * {@code processed_message.event_id} 중복 차단과 Outbox {@code event_id} UNIQUE 제약을 함께
 * 만족시킨다. {@code workflowId} 는 프로세스 테이블({@code model_generation_workflow}) 행 ID.</p>
 *
 * @param messageId         멱등성 보장을 위한 결정적 식별자 (SHA-256 hex 64자)
 * @param workflowId        {@code model_generation_workflow} 행 ID (ADR-010)
 * @param showcase3dModelId 3D 모델 ID
 * @param showcaseId        쇼케이스 ID
 * @param requestedAt       요청 시각
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelGenerationRequestMessage(
        String messageId,
        Long workflowId,
        Long showcase3dModelId,
        Long showcaseId,
        Instant requestedAt
) {

    /**
     * 레코드 생성 시점에 필수 필드 불변식을 강제한다.
     * Kafka 직렬화/역직렬화 및 팩토리 메서드 양쪽 모두에서 null 주입을 차단한다.
     */
    public ModelGenerationRequestMessage {
        Objects.requireNonNull(messageId, "messageId는 필수입니다");
        Objects.requireNonNull(workflowId, "workflowId는 필수입니다");
        Objects.requireNonNull(showcase3dModelId, "showcase3dModelId는 필수입니다");
        Objects.requireNonNull(showcaseId, "showcaseId는 필수입니다");
        Objects.requireNonNull(requestedAt, "requestedAt은 필수입니다");
    }

    /**
     * 결정적 {@code messageId} 와 워크플로우 식별자를 외부에서 주입받아 메시지를 생성한다.
     * {@code requestedAt} 은 발행 시점으로 자동 기록된다.
     */
    public static ModelGenerationRequestMessage of(String messageId,
                                                   Long workflowId,
                                                   Long showcase3dModelId,
                                                   Long showcaseId) {
        return new ModelGenerationRequestMessage(
                messageId,
                workflowId,
                showcase3dModelId,
                showcaseId,
                Instant.now()
        );
    }
}
