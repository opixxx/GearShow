package com.gearshow.backend.showcase.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AsyncPollingConfig} 의 풀 빈 등록 검증.
 *
 * <p>핵심: {@code workflowEventExecutor} 가 {@code tripoPollingExecutor} 와 별개의 빈으로
 * 등록돼 있고, {@code CallerRunsPolicy} 를 보유한다. 운영 사고 (2026-04-28) 의 직접 원인이
 * 단일 풀 공유 + 디폴트 reject policy 였으므로 회귀 방지 차원에서 본 검증을 둔다.</p>
 */
@DisplayName("AsyncPollingConfig")
class AsyncPollingConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AsyncPollingConfig.class);

    @Test
    @DisplayName("workflowEventExecutor 빈 등록 + 풀 크기/큐/CallerRunsPolicy")
    void workflowEventExecutor_isRegisteredWithCorrectAttributes() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasBean("workflowEventExecutor");

            ThreadPoolTaskExecutor executor =
                    ctx.getBean("workflowEventExecutor", ThreadPoolTaskExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
            assertThat(executor.getQueueCapacity()).isEqualTo(50);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("wf-event-");
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
        });
    }

    @Test
    @DisplayName("tripoPollingExecutor 빈 등록 + 단일 consumer 정책 (core=max=1, queue=0)")
    void tripoPollingExecutor_isRegisteredWithSingleConsumerPolicy() {
        // Redis 가 GearShow 의 필수 인프라 (ADR-013) 이므로 본 풀은 무조건 등록된다.
        contextRunner.run(ctx -> {
            assertThat(ctx).hasBean("tripoPollingExecutor");
            ThreadPoolTaskExecutor executor =
                    ctx.getBean("tripoPollingExecutor", ThreadPoolTaskExecutor.class);
            assertThat(executor.getCorePoolSize()).isEqualTo(1);
            assertThat(executor.getMaxPoolSize()).isEqualTo(1);
            assertThat(executor.getQueueCapacity()).isZero();
        });
    }

    @Test
    @DisplayName("workflowEventExecutor 와 tripoPollingExecutor 는 서로 다른 인스턴스")
    void executors_areDifferentInstances() {
        contextRunner.run(ctx -> {
            ThreadPoolTaskExecutor eventPool =
                    ctx.getBean("workflowEventExecutor", ThreadPoolTaskExecutor.class);
            ThreadPoolTaskExecutor pollingPool =
                    ctx.getBean("tripoPollingExecutor", ThreadPoolTaskExecutor.class);
            assertThat(eventPool).isNotSameAs(pollingPool);
        });
    }
}
