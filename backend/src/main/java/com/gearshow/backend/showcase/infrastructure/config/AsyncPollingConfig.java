package com.gearshow.backend.showcase.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 쇼케이스 파이프라인용 비동기 실행기 설정 (설계 §6.2, §7 [8]).
 *
 * <p><b>{@code @EnableAsync} 는 조건 없이 항상 활성</b>한다. 모든 {@code @Async} 호출이
 * 호출자 스레드에서 동기 실행되는 함정을 차단하기 위함.</p>
 *
 * <p>Redis 가 GearShow 의 필수 인프라 (ADR-013) 이므로 모든 executor 빈이 무조건 등록된다.
 * 부팅 시 Redis 미연결이면 ApplicationContext 자체가 부팅에 실패한다.</p>
 */
@Configuration
@EnableAsync
public class AsyncPollingConfig {

    /**
     * {@code WorkflowPollingScheduler} 메인 루프 전용. core=max=1 (단일 consumer).
     * Redis DelayedQueue 의 blocking take 루프를 단일 스레드가 영구 점유한다.
     */
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

    /**
     * 워크플로우 도메인 이벤트 리스너 전용 실행기.
     *
     * <p><b>{@link #tripoPollingExecutor()} 와 분리한 이유</b>: 폴링 풀은 단일 consumer 정책상
     * core=max=1 / queue=0 인데, {@code WorkflowPollingScheduler} 가 앱 시작 시 그 단 하나의
     * 스레드를 영구 점유한다 (Redis DelayedQueue 의 blocking take 루프). 같은 풀을 이벤트
     * 리스너가 공유하면 후속 task 가 즉시 reject 되어 Kafka 리스너로 예외가 propagate 되고
     * DLT 로 라우팅되는 데드락성 동작이 발생했다 (2026-04-28 운영 사고).</p>
     *
     * <p>풀 크기는 워커가 다중 partition 을 병렬 소비할 수 있다는 가정으로 core=2/max=4 로
     * 잡고, 일시 피크는 큐(50) + {@code CallerRunsPolicy} 백프레셔로 흡수한다. offer 작업
     * 자체는 ms 단위라 큰 풀이 필요하지 않다.</p>
     */
    @Bean("workflowEventExecutor")
    public Executor workflowEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("wf-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Downloader 실행기 (설계 §7 [8]). Tripo GET + S3 PUT 이 수 초~수십 초 걸릴 수 있어 Poller
     * 스레드와 분리한다. {@code queueCapacity=50} 은 순간 피크를 흡수하고, 초과 시 caller-runs 로
     * 백프레셔를 준다 — 메시지 드롭 없이 자연스럽게 Poller 가 느려지도록.
     */
    @Bean("downloadExecutor")
    public Executor downloadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("downloader-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
