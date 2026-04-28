package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.port.in.ReconcileStuckWorkflowsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Reconcile 배치 인바운드 어댑터 (설계 §8.4).
 *
 * <p>스케줄러 스레드가 {@code reconcileOnce()} 실행 중 예외로 죽지 않도록 {@code Exception} 을 포착하여
 * 로깅만 한다. 개별 stuck 복구 중 예외는 {@link ReconcileStuckWorkflowsUseCase} 구현체 내부에서
 * 이미 흡수되므로 이 레벨의 catch 는 치명적 예외(예: DB Down 시작시) 방어용이다.</p>
 *
 * <p><b>등록</b>: {@link com.gearshow.backend.showcase.infrastructure.config.ReconcileBeansConfig}
 * 가 명시 등록한다. 활성화 조건은 {@code app.reconcile.enabled=true} +
 * {@code WorkflowPollQueuePort} 빈 존재 (= Redis 활성). 단일 인스턴스 로컬/테스트에선 자연스럽게
 * 비활성화되어 {@code WorkflowPollingScheduler} 와 중복 실행을 피한다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class ReconcileScheduler {

    private final ReconcileStuckWorkflowsUseCase reconcileStuckWorkflowsUseCase;

    @Scheduled(fixedDelayString = "${app.reconcile.fixed-delay-ms:60000}")
    public void reconcile() {
        try {
            int tried = reconcileStuckWorkflowsUseCase.reconcileOnce();
            if (tried > 0) {
                log.info("Reconcile 사이클 완료 — tried: {}", tried);
            }
        } catch (Exception e) {
            log.error("Reconcile 사이클 실행 중 예외 발생", e);
        }
    }
}
