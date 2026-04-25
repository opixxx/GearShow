package com.gearshow.backend.showcase.adapter.in.scheduler;

import com.gearshow.backend.showcase.application.port.in.ReconcileStuckWorkflowsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link ReconcileScheduler} 단위 테스트.
 *
 * <p>스케줄러 자체의 cron 실행은 Spring 가 담당하므로, 본 테스트는 {@code reconcile()} 메서드를
 * 직접 호출해 (a) UseCase 위임 (b) 예외 흡수 두 가지 책임만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReconcileScheduler")
class ReconcileSchedulerTest {

    @Mock
    private ReconcileStuckWorkflowsUseCase reconcileStuckWorkflowsUseCase;

    @InjectMocks
    private ReconcileScheduler scheduler;

    @Test
    @DisplayName("reconcile() 은 UseCase 에 한 번 위임한다")
    void delegatesToUseCase() {
        given(reconcileStuckWorkflowsUseCase.reconcileOnce()).willReturn(3);

        scheduler.reconcile();

        verify(reconcileStuckWorkflowsUseCase, times(1)).reconcileOnce();
    }

    @Test
    @DisplayName("UseCase 예외가 스케줄러 스레드 밖으로 새지 않는다 (스케줄러 죽지 않음)")
    void exceptionsSwallowed() {
        given(reconcileStuckWorkflowsUseCase.reconcileOnce())
                .willThrow(new RuntimeException("DB 일시 장애"));

        scheduler.reconcile();  // throw 금지

        verify(reconcileStuckWorkflowsUseCase, times(1)).reconcileOnce();
    }
}
