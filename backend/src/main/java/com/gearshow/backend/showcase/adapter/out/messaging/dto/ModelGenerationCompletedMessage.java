package com.gearshow.backend.showcase.adapter.out.messaging.dto;

import java.time.Instant;

/**
 * 3D 모델 생성이 최종 완료(COMPLETED) 되었을 때 Outbox 를 경유해 발행되는 Kafka 메시지.
 *
 * <p>Downloader 가 TX_final 안에서 {@code ModelGenerationOutboxPublisher.publishCompleted} 로
 * 생성한다. 후속 Consumer (FCM 알림 등) 는 {@code workflowId} 기준으로 idempotent 처리한다.</p>
 *
 * @param messageId         Outbox {@code event_id} — 결정적 파생 (ADR-011 ③)
 * @param workflowId        완료된 워크플로우 ID
 * @param showcaseId        대상 쇼케이스 ID
 * @param modelFileUrl      S3 미러링된 GLB 파일 URL
 * @param previewImageUrl   S3 미러링된 프리뷰 이미지 URL (없으면 null)
 * @param completedAt       TX_final 시각 (Poller 의 tripo_succeeded_at 이 아니라 TX_final 기준)
 */
public record ModelGenerationCompletedMessage(
        String messageId,
        Long workflowId,
        Long showcaseId,
        String modelFileUrl,
        String previewImageUrl,
        Instant completedAt
) {
}
