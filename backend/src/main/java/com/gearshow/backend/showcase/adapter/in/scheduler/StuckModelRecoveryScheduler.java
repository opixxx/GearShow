package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.port.in.RecoverStuckModelsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 좀비 3D 모델 복구 스케줄러 (Inbound Adapter).
 *
 * <p>주기적으로 {@link RecoverStuckModelsUseCase#recoverOnce()} 를 호출하여
 * REQUESTED stuck / GENERATING 좀비 상태를 감지하고 복구한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckModelRecoveryScheduler {

    private final RecoverStuckModelsUseCase recoverStuckModelsUseCase;

    @Scheduled(fixedDelayString = "${app.stuck-recovery.interval-ms:60000}")
    public void recover() {
        try {
            int recovered = recoverStuckModelsUseCase.recoverOnce();
            if (recovered > 0) {
                log.info("좀비 모델 복구 사이클 완료 - recovered: {}", recovered);
            }
        } catch (Exception e) {
            // 스케줄러 스레드가 죽지 않도록 예외를 포착
            log.error("좀비 모델 복구 사이클 실행 중 예외 발생", e);
        }
    }
}
