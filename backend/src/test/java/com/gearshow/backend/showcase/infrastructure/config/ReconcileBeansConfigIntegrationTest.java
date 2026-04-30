package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.adapter.in.scheduler.ReconcileScheduler;
import com.gearshow.backend.showcase.application.service.ReconcileStuckWorkflowsService;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReconcileBeansConfig} 의 빈 등록을 진짜 {@code @SpringBootApplication} 컨텍스트에서
 * 검증하는 통합 테스트.
 *
 * <p>ADR-013 으로 Redis 가 필수 인프라가 되면서 활성화 조건이
 * {@code app.reconcile.enabled=true} 단일 플래그로 단순화됐다. 본 테스트는 그 단일 조건이
 * prod 의 {@code @ComponentScan} 처리 순서에서도 안정적으로 빈을 등록함을 확인한다.</p>
 *
 * <p>테스트 환경: Testcontainers Redis + Testcontainers MySQL (application-test.yml).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("ReconcileBeansConfig prod 환경 통합 검증")
class ReconcileBeansConfigIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("app.reconcile.enabled", () -> "true");
        // 실제 Reconcile/Poller 루프가 도는 걸 막는다 — Bean 등록 여부만 검증.
        registry.add("app.workflow-polling.scheduler-enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("app.reconcile.enabled=true → Reconcile 두 빈 등록 (prod 처리 순서 재현)")
    void reconcileBeansPresentInProdContextWhenEnabled() {
        assertThat(applicationContext.getBeansOfType(ReconcileStuckWorkflowsService.class))
                .as("ReconcileStuckWorkflowsService 가 등록되어야 함")
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(ReconcileScheduler.class))
                .as("ReconcileScheduler 가 등록되어야 함")
                .hasSize(1);
    }
}
