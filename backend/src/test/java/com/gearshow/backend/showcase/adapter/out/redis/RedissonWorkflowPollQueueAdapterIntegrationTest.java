package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.WorkflowPollQueuePort;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedissonWorkflowPollQueueAdapter} 실제 Redis 기반 통합 테스트.
 *
 * <p>검증:</p>
 * <ul>
 *   <li>offer(delay) 후 delay 경과하면 take 가 반환</li>
 *   <li>delay 전에는 take 가 블로킹 상태 유지</li>
 *   <li>FIFO 순서로 여러 원소 방출</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("RedissonWorkflowPollQueueAdapter 통합")
class RedissonWorkflowPollQueueAdapterIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        // 실제 Poller 루프가 큐 메시지를 가로채지 않도록 통합 테스트에서는 자동 소비자를 끈다.
        registry.add("app.workflow-polling.scheduler-enabled", () -> "false");
    }

    @Autowired
    private WorkflowPollQueuePort pollQueue;

    @Test
    @DisplayName("offer(500ms) 후 take 는 약 500ms 뒤에 원소를 반환한다")
    void shortDelay_takeReturnsAfterElapsed() throws Exception {
        Long workflowId = 5_001L;
        long beforeOffer = System.currentTimeMillis();
        pollQueue.offer(workflowId, Duration.ofMillis(500));

        Long taken = pollQueue.take();
        long elapsed = System.currentTimeMillis() - beforeOffer;

        assertThat(taken).isEqualTo(workflowId);
        // 대역폭 제외하고 최소 400ms 는 대기했어야 한다 (timing 여유 100ms).
        assertThat(elapsed).isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("offer(3s) 후 500ms 시점에는 take 가 아직 블로킹 중이다")
    void longDelay_takeBlocksBeforeElapsed() throws Exception {
        Long workflowId = 5_002L;
        pollQueue.offer(workflowId, Duration.ofSeconds(3));

        AtomicReference<Long> taken = new AtomicReference<>();
        CountDownLatch taken500ms = new CountDownLatch(1);
        Thread consumer = new Thread(() -> {
            try {
                taken.set(pollQueue.take());
                taken500ms.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        consumer.start();

        boolean reachedBefore500ms = taken500ms.await(500, TimeUnit.MILLISECONDS);
        assertThat(reachedBefore500ms).isFalse();
        assertThat(taken.get()).isNull();

        consumer.interrupt();
        consumer.join(5_000);
    }

    @Test
    @DisplayName("여러 원소를 같은 짧은 delay 로 offer 하면 모두 take 로 받을 수 있다")
    void multipleOffers_allTaken() throws Exception {
        pollQueue.offer(6_001L, Duration.ofMillis(100));
        pollQueue.offer(6_002L, Duration.ofMillis(100));
        pollQueue.offer(6_003L, Duration.ofMillis(100));

        Long a = pollQueue.take();
        Long b = pollQueue.take();
        Long c = pollQueue.take();

        assertThat(a).isIn(6_001L, 6_002L, 6_003L);
        assertThat(b).isIn(6_001L, 6_002L, 6_003L);
        assertThat(c).isIn(6_001L, 6_002L, 6_003L);
        assertThat(a).isNotEqualTo(b);
        assertThat(b).isNotEqualTo(c);
    }
}
