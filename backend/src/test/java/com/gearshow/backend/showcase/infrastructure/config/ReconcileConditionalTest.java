package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.in.scheduler.ReconcileScheduler;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.application.service.ReconcileStuckWorkflowsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Reconcile Bean 등록 조건 회귀 테스트 (lightweight).
 *
 * <p>ADR-013 으로 Redis 가 필수 인프라가 되면서 활성화 조건이
 * {@code app.reconcile.enabled} 단일 플래그로 단순화됐다. 본 테스트는 비활성 시 빈이
 * 등록되지 않음을 lightweight 컨텍스트로 빠르게 검증한다.</p>
 *
 * <p>활성 happy path (플래그 켜짐 + 빈 등록 검증) 는 {@link ReconcileBeansConfigIntegrationTest}
 * 가 진짜 {@code @SpringBootApplication} 컨텍스트에서 담당한다.</p>
 */
@DisplayName("Reconcile Bean 활성화 조건 — app.reconcile.enabled 플래그")
class ReconcileConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ReconcileBeansConfig.class,
                    ReconcilePropertiesConfig.class,
                    WorkflowPollQueuePortStubConfig.class);

    @Test
    @DisplayName("app.reconcile.enabled=false → Reconcile Bean 미등록")
    void reconcileBeansAbsentWhenDisabled() {
        contextRunner
                .withPropertyValues("app.reconcile.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("플래그 미설정(누락) → 보수적 default 로 Bean 미등록")
    void reconcileBeansAbsentWhenFlagMissing() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
            assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
        });
    }

    /**
     * {@link ReconcileProperties} 자동 등록 — 운영에서는 {@code @ConfigurationPropertiesScan} 이
     * 처리하지만 lightweight {@link ApplicationContextRunner} 컨텍스트엔 별도 활성화 필요.
     */
    @Configuration
    @EnableConfigurationProperties(ReconcileProperties.class)
    static class ReconcilePropertiesConfig {
    }

    /**
     * {@link ReconcileBeansConfig} 가 의존하는 {@link WorkflowPollQueuePort} mock 등록.
     * 운영에선 {@code RedissonWorkflowPollQueueAdapter} 가 제공.
     */
    @Configuration
    static class WorkflowPollQueuePortStubConfig {

        @Bean
        WorkflowPollQueuePort workflowPollQueuePort() {
            return mock(WorkflowPollQueuePort.class);
        }
    }
}
