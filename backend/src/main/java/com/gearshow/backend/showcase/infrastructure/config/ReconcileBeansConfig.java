package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.in.scheduler.ReconcileScheduler;
import com.gearshow.backend.showcase.application.port.in.ReconcileStuckWorkflowsUseCase;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.application.service.ReconcileStuckWorkflowsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reconcile 빈 등록 구성.
 *
 * <p>{@link ReconcileStuckWorkflowsService} 와 {@link ReconcileScheduler} 의 활성화는
 * {@code app.reconcile.enabled=true} 단일 플래그로 통제된다. Redis 는 GearShow 의 필수 인프라
 * (ADR-013) 이므로 {@link WorkflowPollQueuePort} 가 항상 빈으로 존재해 별도
 * {@code @ConditionalOnBean} 분기가 필요 없다.</p>
 *
 * <p>{@link ReconcileProperties} 는 {@code GearShowApplication} 의
 * {@code @ConfigurationPropertiesScan} 으로 자동 등록되므로 별도
 * {@code @EnableConfigurationProperties} 가 필요 없다.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.reconcile", name = "enabled", havingValue = "true")
public class ReconcileBeansConfig {

    @Bean
    public ReconcileStuckWorkflowsService reconcileStuckWorkflowsService(
            ModelGenerationWorkflowPort workflowPort,
            TripoPendingTaskPort tripoPendingTaskPort,
            WorkflowLockPort workflowLockPort,
            WorkflowPollQueuePort workflowPollQueuePort,
            ApplicationEventPublisher eventPublisher,
            ReconcileProperties properties,
            TripoAdmissionQueuePort admissionQueuePort,
            AdmissionQueueProperties admissionProperties) {
        return new ReconcileStuckWorkflowsService(
                workflowPort,
                tripoPendingTaskPort,
                workflowLockPort,
                workflowPollQueuePort,
                eventPublisher,
                properties,
                admissionQueuePort,
                admissionProperties);
    }

    @Bean
    public ReconcileScheduler reconcileScheduler(
            ReconcileStuckWorkflowsUseCase reconcileStuckWorkflowsUseCase) {
        return new ReconcileScheduler(reconcileStuckWorkflowsUseCase);
    }
}
