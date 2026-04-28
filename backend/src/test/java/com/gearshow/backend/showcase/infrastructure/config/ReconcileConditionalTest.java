package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.in.scheduler.ReconcileScheduler;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationWorkflowPort;
import com.gearshow.backend.showcase.application.port.out.TripoPendingTaskPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.showcase.application.service.ReconcileStuckWorkflowsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Reconcile Bean 등록 조건 회귀 테스트.
 *
 * <p><b>회귀 방지 대상</b>: PR #47 (P1-G Reconcile) 머지 후 CD 3회 연속 실패 (#47, #48, idempotency
 * refactor) — {@code app.reconcile.enabled=true} + {@code gearshow.redis.enabled=false} 조합에서
 * {@link WorkflowPollQueuePort} 빈 미등록 → DI 실패 → 부팅 실패. 이번 수정으로 두 플래그를
 * {@code @ConditionalOnExpression} 의 AND 조건으로 묶어 모순 자체를 차단했다.</p>
 *
 * <p>{@link ApplicationContextRunner} 로 lightweight 컨텍스트를 띄워 {@code @SpringBootTest} 의
 * 무거움(JPA / Kafka / Web 컨텍스트 전체 로드) 없이 조건 분기만 검증한다.</p>
 */
@DisplayName("Reconcile Bean 활성화 조건 — Redis AND Reconcile")
class ReconcileConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReconcileTestConfig.class);

    @Test
    @DisplayName("Redis 비활성 + Reconcile 활성 → Reconcile Bean 미등록 (현 prod 시나리오, 부팅 정합성)")
    void reconcileBeansAbsentWhenRedisDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.reconcile.enabled=true",
                        "gearshow.redis.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("Reconcile 비활성 + Redis 활성 → Reconcile Bean 미등록")
    void reconcileBeansAbsentWhenReconcileDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.reconcile.enabled=false",
                        "gearshow.redis.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("두 플래그 모두 비활성 → Reconcile Bean 미등록")
    void reconcileBeansAbsentWhenBothDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.reconcile.enabled=false",
                        "gearshow.redis.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("두 플래그 모두 활성 → Reconcile Bean 등록 (다중 인스턴스 운영 시나리오)")
    void reconcileBeansPresentWhenBothEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.reconcile.enabled=true",
                        "gearshow.redis.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReconcileScheduler.class);
                    assertThat(context).hasSingleBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("플래그 미설정(누락) → 보수적 default 로 Bean 미등록")
    void reconcileBeansAbsentWhenFlagsMissing() {
        // 두 플래그 모두 set 하지 않음 — @ConditionalOnExpression 의 SpEL default(:false)
        // 가 적용되어야 한다는 정책을 못박는 가드. 누가 SpEL 의 ":false" 를 떼면 즉시 깨짐.
        contextRunner
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    /**
     * Reconcile 두 클래스의 Conditional 만 격리 검증하기 위한 최소 컨텍스트.
     *
     * <p>실제 Redis/JPA/Kafka 어댑터를 로드하지 않고 의존 포트들을 mock 으로 대체한다.
     * Conditional 평가는 Bean 정의 등록 시점에 일어나므로, mock 으로 채워도 조건 분기 자체는
     * 정확히 검증된다.</p>
     */
    @Configuration
    @EnableConfigurationProperties(ReconcileProperties.class)
    @Import({ReconcileScheduler.class, ReconcileStuckWorkflowsService.class})
    static class ReconcileTestConfig {

        @Bean
        ModelGenerationWorkflowPort modelGenerationWorkflowPort() {
            return mock(ModelGenerationWorkflowPort.class);
        }

        @Bean
        TripoPendingTaskPort tripoPendingTaskPort() {
            return mock(TripoPendingTaskPort.class);
        }

        @Bean
        WorkflowLockPort workflowLockPort() {
            return mock(WorkflowLockPort.class);
        }

        @Bean
        WorkflowPollQueuePort workflowPollQueuePort() {
            return mock(WorkflowPollQueuePort.class);
        }
    }
}
