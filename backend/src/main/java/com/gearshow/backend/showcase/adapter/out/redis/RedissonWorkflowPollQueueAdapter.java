package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link WorkflowPollQueuePort} 의 Redisson {@code RDelayedQueue} 기반 구현 (설계 §3.4
 * {@code poll:delayed-queue}).
 *
 * <p>RDelayedQueue 는 내부적으로 backing {@code RBlockingQueue} 로 delay 경과 원소를 이동시켜
 * 컨슈머가 {@link RBlockingQueue#take()} 로 일반 blocking queue 처럼 받을 수 있게 한다.
 * 단일 Poller 스레드 기준이며 소비자 확장은 후속 과제.</p>
 *
 * <p>{@code gearshow.redis.enabled=true} 일 때만 Bean 으로 등록된다. disabled 환경에서는
 * Poller 스케줄러 Bean 도 같은 조건으로 부재 — 전체 폴링 계층이 조용히 꺼진다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "gearshow.redis.enabled", havingValue = "true")
public class RedissonWorkflowPollQueueAdapter implements WorkflowPollQueuePort {

    static final String QUEUE_KEY = "poll:delayed-queue:main";

    private final RBlockingQueue<Long> blockingQueue;
    private final RDelayedQueue<Long> delayedQueue;

    public RedissonWorkflowPollQueueAdapter(RedissonClient redissonClient) {
        this.blockingQueue = redissonClient.getBlockingQueue(QUEUE_KEY);
        this.delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
    }

    @Override
    public void offer(Long workflowId, Duration delay) {
        delayedQueue.offer(workflowId, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Long take() throws InterruptedException {
        return blockingQueue.take();
    }

    /**
     * {@link RDelayedQueue} 는 애플리케이션 종료 시 명시적으로 destroy 해야 내부 타이머 태스크가
     * 해제된다. 메시지 자체는 Redis 에 남아 재기동 시 그대로 재개된다.
     */
    @PreDestroy
    void destroy() {
        try {
            delayedQueue.destroy();
        } catch (Exception e) {
            log.warn("RDelayedQueue destroy 중 예외 — 무시 가능", e);
        }
    }
}
