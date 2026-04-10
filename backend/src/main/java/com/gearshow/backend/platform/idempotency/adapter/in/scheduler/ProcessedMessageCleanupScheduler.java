package com.gearshow.backend.platform.idempotency.adapter.in.scheduler;

import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처리된 메시지 이력 정리 스케줄러 (Inbound Adapter).
 *
 * <p>시간 기반 트리거(cron)로 정리 유스케이스를 호출한다.
 * 비즈니스 로직은 {@link CleanupProcessedMessageUseCase} 구현체에 위임하며,
 * 이 클래스는 트리거 방식만 담당한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ProcessedMessageCleanupScheduler {

    private final CleanupProcessedMessageUseCase cleanupProcessedMessageUseCase;

    /**
     * 매일 새벽 3시 (Asia/Seoul)에 처리 이력 정리 유스케이스를 실행한다.
     */
    @Scheduled(
            cron = "${app.idempotency.cleanup-cron:0 0 3 * * *}",
            zone = "${app.idempotency.cleanup-zone:Asia/Seoul}"
    )
    public void cleanupOldMessages() {
        cleanupProcessedMessageUseCase.cleanup();
    }
}
