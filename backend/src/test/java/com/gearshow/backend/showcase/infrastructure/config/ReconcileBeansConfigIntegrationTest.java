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
 * {@link ReconcileBeansConfig} 의 시나리오 #4 ("두 플래그 모두 활성 → Reconcile Bean 등록") 를
 * 진짜 {@code @SpringBootApplication} 컨텍스트에서 검증하는 통합 테스트.
 *
 * <p><b>왜 별도 통합 테스트가 필요한가</b>: lightweight {@code ApplicationContextRunner} (
 * {@link ReconcileConditionalTest}) 는 prod 의 {@code @ComponentScan} → {@code @Configuration}
 * 처리 순서를 정확히 재현하지 못한다. 일반 {@code @Configuration} + {@code @ConditionalOnBean}
 * 조합은 처리 순서에 따라 결과가 달라질 수 있어 (Spring Boot 공식 입장이 "Configuration 안에서만
 * 사용 권장" 인 이유), prod 환경의 진짜 처리 순서에서도 시나리오 #4 가 통과하는지 별도 검증한다.</p>
 *
 * <p>이 테스트가 통과하면 {@link ReconcileBeansConfig} 가 일반 {@code @Configuration} 으로도
 * prod 환경에서 안정 동작함이 확인된다. 통과하지 못하면 {@code @AutoConfiguration} 으로의 전환을
 * 고려해야 한다.</p>
 *
 * <p>테스트 환경: Testcontainers Redis + Testcontainers MySQL (application-test.yml).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("ReconcileBeansConfig 시나리오 #4 prod 환경 통합 검증")
class ReconcileBeansConfigIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.enabled", () -> "true");
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("app.reconcile.enabled", () -> "true");
        // 실제 Reconcile/Poller 루프가 도는 걸 막는다 — Bean 등록 여부만 검증.
        registry.add("app.workflow-polling.scheduler-enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("두 플래그 모두 활성 → ReconcileBeansConfig 활성화되어 Reconcile 두 빈 등록 (prod 처리 순서 재현)")
    void reconcileBeansPresentInProdContextWhenBothEnabled() {
        // ReconcileBeansConfig 의 @ConditionalOnBean(WorkflowPollQueuePort.class) 가
        // prod 의 @ComponentScan 처리 순서에서도 안정적으로 통과하는지 검증.
        // 이 단언이 깨지면 일반 @Configuration 패턴이 prod 에서 신뢰 불가 → @AutoConfiguration 전환 검토.
        assertThat(applicationContext.getBeansOfType(ReconcileStuckWorkflowsService.class))
                .as("두 플래그 활성 시 ReconcileStuckWorkflowsService 가 등록되어야 함")
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(ReconcileScheduler.class))
                .as("두 플래그 활성 시 ReconcileScheduler 가 등록되어야 함")
                .hasSize(1);
    }
}
