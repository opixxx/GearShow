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
 * Reconcile Bean 등록 조건 회귀 테스트.
 *
 * <p><b>회귀 방지 대상</b>: PR #47 (P1-G Reconcile) 머지 후 CD 3회 연속 실패 (#47, #48, idempotency
 * refactor) — {@code app.reconcile.enabled=true} + Redis 비활성 조합에서
 * {@link WorkflowPollQueuePort} 빈 미등록 → DI 실패 → 부팅 실패.
 *
 * <p><b>활성화 패턴</b> (PR #50 후속 — deferred_refactor #18):
 * {@link ReconcileBeansConfig} 가 {@code @ConditionalOnProperty(app.reconcile.enabled)} +
 * {@code @ConditionalOnBean(WorkflowPollQueuePort.class)} 두 조건을 동시에 요구한다.
 * application 코드는 인프라 키워드 ({@code gearshow.redis.enabled}) 를 직접 참조하지 않고
 * 빈 의존성으로만 활성 여부를 표현한다. {@code WorkflowPollQueuePort} 빈은 Redis 어댑터
 * ({@code RedissonWorkflowPollQueueAdapter}) 의 {@code @ConditionalOnProperty(gearshow.redis.enabled=true)}
 * 로 등록되므로, 본 테스트는 그 빈을 mock 으로 등록/미등록하여 Redis 활성/비활성 환경을 시뮬레이션한다.</p>
 *
 * <p>{@link ApplicationContextRunner} 로 lightweight 컨텍스트를 띄워 {@code @SpringBootTest} 의
 * 무거움(JPA / Kafka / Web 컨텍스트 전체 로드) 없이 조건 분기만 검증한다.</p>
 *
 * <p><b>시나리오 #4 ("두 플래그 모두 활성 → 등록") 는 별도 통합 테스트로 위임</b>:
 * lightweight 컨텍스트는 prod 의 {@code @ComponentScan} → {@code @Configuration} 처리 순서를
 * 정확히 재현하지 못해 일반 {@code @Configuration} + {@code @ConditionalOnBean} 의 happy
 * path 가 환경에 따라 깨질 수 있다. happy path 검증은 {@link ReconcileBeansConfigIntegrationTest}
 * 가 진짜 {@code @SpringBootApplication} 컨텍스트에서 수행한다.</p>
 *
 * <p><b>시나리오별 mock 빈 셋 분리 원칙</b>: 모든 시나리오에 mock {@link WorkflowPollQueuePort} 를
 * 일괄 제공하면 {@code @ConditionalOnBean} 평가가 무의미해져 시나리오 #1 ("Redis 비활성") 이 거짓
 * 통과한다. 따라서 "Redis 활성" 시나리오 (시나리오 #2) 만 mock {@link WorkflowPollQueuePort} 를
 * 명시 등록한다.</p>
 */
@DisplayName("Reconcile Bean 활성화 조건 — Reconcile 플래그 AND WorkflowPollQueuePort 빈")
class ReconcileConditionalTest {

    /**
     * 모든 시나리오 공통 베이스 — {@link ReconcileBeansConfig} 와 properties 설정 주입.
     * mock 빈은 시나리오별로 추가 (있어야 의미 있는 시나리오만).
     *
     * <p><b>중요</b>: {@code ApplicationContextRunner} 의 lightweight 컨텍스트는 prod 의
     * {@code @SpringBootApplication} 처리 순서를 정확히 재현하지 못할 수 있다. prod 환경에서의
     * 시나리오 #4 (두 플래그 모두 활성 → 등록) 검증은 {@link ReconcileBeansConfigIntegrationTest}
     * 가 담당.</p>
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ReconcileBeansConfig.class, ReconcilePropertiesConfig.class);

    @Test
    @DisplayName("Redis 비활성 + Reconcile 활성 → Reconcile Bean 미등록 (현 prod 시나리오, 부팅 정합성)")
    void reconcileBeansAbsentWhenRedisDisabled() {
        // WorkflowPollQueuePort mock 빈 미제공 → @ConditionalOnBean false → ReconcileBeansConfig 비활성.
        contextRunner
                .withPropertyValues("app.reconcile.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("Reconcile 비활성 + Redis 활성 → Reconcile Bean 미등록")
    void reconcileBeansAbsentWhenReconcileDisabled() {
        // WorkflowPollQueuePort mock 빈 제공이라도 @ConditionalOnProperty(app.reconcile.enabled) false → 비활성.
        contextRunner
                .withUserConfiguration(WorkflowPollQueuePortStubConfig.class)
                .withPropertyValues("app.reconcile.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    @Test
    @DisplayName("두 플래그 모두 비활성 → Reconcile Bean 미등록")
    void reconcileBeansAbsentWhenBothDisabled() {
        contextRunner
                .withPropertyValues("app.reconcile.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ReconcileScheduler.class);
                    assertThat(context).doesNotHaveBean(ReconcileStuckWorkflowsService.class);
                });
    }

    // 시나리오 #4 ("두 플래그 모두 활성 → 등록") 는 ReconcileBeansConfigIntegrationTest 가 담당한다.
    // ApplicationContextRunner 의 lightweight 컨텍스트가 prod 의 @ComponentScan → @Configuration 처리
    // 순서를 정확히 재현하지 못해 happy path 가 거짓 fail 할 수 있기 때문이다.

    @Test
    @DisplayName("플래그 미설정(누락) → 보수적 default 로 Bean 미등록")
    void reconcileBeansAbsentWhenFlagsMissing() {
        // app.reconcile.enabled 미설정 → @ConditionalOnProperty 의 default false → 비활성.
        // 참고: havingValue/prefix 변경은 시나리오 #4 가 함께 잡아준다 — true 명시인데도 등록 실패면 #4 가 fail.
        contextRunner
                .run(context -> {
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
     * "Redis 활성" 시뮬레이션 — mock {@link WorkflowPollQueuePort} 만 제공한다. 시나리오 #2 는
     * {@code @ConditionalOnProperty} 단계에서 비활성화되므로 빈 인스턴스화에 다른 의존 mock 은
     * 필요 없다 (활성 happy path 검증은 {@link ReconcileBeansConfigIntegrationTest} 가 담당).
     */
    @Configuration
    static class WorkflowPollQueuePortStubConfig {

        @Bean
        WorkflowPollQueuePort workflowPollQueuePort() {
            return mock(WorkflowPollQueuePort.class);
        }
    }
}
