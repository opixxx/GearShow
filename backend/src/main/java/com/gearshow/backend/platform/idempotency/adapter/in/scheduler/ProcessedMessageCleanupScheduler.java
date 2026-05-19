package com.gearshow.backend.platform.idempotency.adapter.in.scheduler;

import com.gearshow.backend.platform.idempotency.application.port.in.CleanupProcessedMessageUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 처리된 메시지 이력 정리 스케줄러 (Inbound Adapter).
 *
 * <p>시간 기반 트리거(cron)로 정리 유스케이스를 호출한다.
 * 비즈니스 로직은 {@link CleanupProcessedMessageUseCase} 구현체에 위임하며,
 * 이 클래스는 트리거 방식만 담당한다.</p>
 *
 * <p><b>역할(api/worker) 분리 토글 (ADR-027)</b>: 정리 배치는 공용 플랫폼 인프라이므로
 * always-on 인 api 프로세스가 단독 소유한다. worker 는 {@code app.idempotency.cleanup-enabled=false}
 * 로 비등록하여 2-프로세스가 같은 정리 배치를 중복 실행하지 않게 한다. 미설정 시 활성
 * ({@code matchIfMissing=true}) — 운영 무회귀(현 모놀리스 동작 유지).</p>
 */
@Component
@ConditionalOnProperty(name = "app.idempotency.cleanup-enabled",
        havingValue = "true", matchIfMissing = true)
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
