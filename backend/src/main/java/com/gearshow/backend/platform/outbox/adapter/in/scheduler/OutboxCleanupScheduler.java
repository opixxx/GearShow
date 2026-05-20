package com.gearshow.backend.platform.outbox.adapter.in.scheduler;

import com.gearshow.backend.platform.outbox.application.port.in.CleanupOutboxUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 발행 완료된 Outbox 메시지 정리 스케줄러.
 *
 * <p>Kafka 활성화 여부와 무관하게 주기적으로 실행된다.
 * Kafka 비활성화 환경에서는 Outbox 레코드 자체가 쌓이지 않으므로 no-op 에 가깝다.</p>
 *
 * <p><b>역할(api/worker) 분리 토글 (ADR-027)</b>: 정리 배치는 공용 플랫폼 인프라이므로
 * always-on 인 api 프로세스가 단독 소유한다. worker 는 {@code app.outbox.cleanup-enabled=false}
 * 로 비등록하여 2-프로세스가 같은 정리 배치를 중복 실행하지 않게 한다. 미설정 시 활성
 * ({@code matchIfMissing=true}) — 운영 무회귀(현 모놀리스 동작 유지).</p>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
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
