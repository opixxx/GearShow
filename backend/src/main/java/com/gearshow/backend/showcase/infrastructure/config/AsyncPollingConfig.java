package com.gearshow.backend.showcase.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Tripo 폴링용 비동기 실행기 설정 (설계 §6.2).
 *
 * <p>{@code WorkflowPollingScheduler} 가 {@code ApplicationReadyEvent} 수신 즉시 단일
 * 장수명 스레드에서 {@code RBlockingQueue.take()} 블로킹 루프를 실행한다. Spring 기본
 * 실행기를 사용하면 컨테이너가 종료 시 공유 풀이 stuck 될 수 있으므로 전용 executor 를 분리한다.</p>
 *
 * <p>{@code gearshow.redis.enabled=true} 일 때만 스캔되도록 해, Redis 미사용 환경에서는
 * {@code @EnableAsync} 자체가 활성화되지 않는다 (테스트 격리). Redis 어댑터/큐만 띄우고
 * 자동 Poller 루프만 끄고 싶은 통합 테스트는 {@code WorkflowPollingScheduler} 쪽 조건
 * ({@code app.tripo-polling.scheduler-enabled=false}) 을 사용한다. 이 때 실행기 Bean 은
 * 남지만 구독자가 없어 idle 상태.</p>
 */
@Configuration
@EnableAsync
@ConditionalOnProperty(name = "gearshow.redis.enabled", havingValue = "true")
public class AsyncPollingConfig {

    /** {@code WorkflowPollingScheduler} 메인 루프 전용. core=max=1 (단일 consumer). */
    @Bean("tripoPollingExecutor")
    public Executor tripoPollingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("tripo-poller-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
