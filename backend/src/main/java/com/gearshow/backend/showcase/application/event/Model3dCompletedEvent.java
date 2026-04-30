package com.gearshow.backend.showcase.application.event;

import java.util.Objects;

/**
 * 3D 모델 생성 워크플로우가 COMPLETED 로 전이된 시점에 발행되는 ApplicationEvent.
 *
 * <p>{@code DownloadFinalizer.finalizeDownload} 의 TX_final 안에서 발행되며,
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 TX 커밋 직후
 * {@code Showcase.has_3d_model} 비정규화 플래그를 동기화한다 (목록 응답에 3D 뱃지 노출용).</p>
 *
 * <p>Spring {@code ApplicationEventPublisher} 를 경유해 동일 JVM 안에서만 전달된다.
 * Outbox/Kafka 로 발행되는 {@code ModelGenerationCompletedMessage} 와는 별개이며,
 * 후자는 외부 시스템/다른 컨텍스트용 표면이다.</p>
 *
 * @param showcaseId 3D 모델 완성이 반영될 Showcase 행 ID — null 금지
 */
public record Model3dCompletedEvent(Long showcaseId) {
    public Model3dCompletedEvent {
        Objects.requireNonNull(showcaseId, "showcaseId 는 필수입니다.");
    }
}
