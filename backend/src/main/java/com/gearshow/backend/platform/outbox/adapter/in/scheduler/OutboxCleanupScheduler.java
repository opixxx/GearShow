package com.gearshow.backend.platform.outbox.adapter.in.scheduler;

import com.gearshow.backend.platform.outbox.application.port.in.CleanupOutboxUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발행 완료된 Outbox 메시지 정리 스케줄러.
 *
 * <p>Kafka 활성화 여부와 무관하게 주기적으로 실행된다.
 * Kafka 비활성화 환경에서는 Outbox 레코드 자체가 쌓이지 않으므로 no-op 에 가깝다.</p>
 */
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private final CleanupOutboxUseCase cleanupOutboxUseCase;

    @Scheduled(
            cron = "${app.outbox.cleanup-cron:0 0 4 * * *}",
            zone = "${app.outbox.cleanup-zone:Asia/Seoul}"
    )
    public void cleanupPublished() {
        cleanupOutboxUseCase.cleanup();
    }
}
