package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort.WorkflowLockBusyException;
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedissonWorkflowLockAdapter} 실제 Redis 기반 통합 테스트.
 *
 * <p>Testcontainers 로 Redis 컨테이너를 기동하고 {@code gearshow.redis.enabled=true} 로
 * 오버라이드해 Redisson 클라이언트와 어댑터가 실제 로드되도록 한다. 검증:</p>
 * <ul>
 *   <li>단일 호출 시 락 획득·해제 후 action 실행</li>
 *   <li>동시에 두 호출이 겹치면 두 번째는 {@link WorkflowLockBusyException}</li>
 *   <li>첫 호출 종료 후엔 같은 workflowId 재획득 가능</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("RedissonWorkflowLockAdapter 통합")
class RedissonWorkflowLockAdapterIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.enabled", () -> "true");
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    @Autowired
    private WorkflowLockPort workflowLockPort;

    @Test
    @DisplayName("단일 호출: 락 획득·해제 후 action 이 정확히 한 번 실행된다")
    void singleCall_runsActionOnce() {
        AtomicInteger counter = new AtomicInteger();
        workflowLockPort.withLock(1_001L, counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 호출: 첫 스레드가 락 보유 중이면 두 번째 스레드는 WorkflowLockBusyException")
    void concurrentCall_secondThreadRejected() throws Exception {
        Long workflowId = 1_002L;
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstMayExit = new CountDownLatch(1);
        AtomicReference<Throwable> secondResult = new AtomicReference<>();

        Thread first = new Thread(() ->
                workflowLockPort.withLock(workflowId, () -> {
                    firstEntered.countDown();
                    try {
                        firstMayExit.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
        first.start();
        assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();

        Thread second = new Thread(() -> {
            try {
                workflowLockPort.withLock(workflowId, () -> { /* noop */ });
            } catch (Throwable t) {
                secondResult.set(t);
            }
        });
        second.start();
        second.join(5_000);

        assertThat(secondResult.get()).isInstanceOf(WorkflowLockBusyException.class);

        firstMayExit.countDown();
        first.join(5_000);
    }

    @Test
    @DisplayName("첫 호출 종료 후 같은 workflowId 로 재획득 가능")
    void afterRelease_reacquirable() {
        Long workflowId = 1_003L;
        workflowLockPort.withLock(workflowId, () -> {});
        AtomicInteger counter = new AtomicInteger();
        workflowLockPort.withLock(workflowId, counter::incrementAndGet);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 workflowId 는 서로 간섭하지 않는다 (중첩 락 획득 가능)")
    void differentWorkflowIds_independent() {
        AtomicInteger inner = new AtomicInteger();
        workflowLockPort.withLock(2_001L, () ->
                workflowLockPort.withLock(2_002L, inner::incrementAndGet));
        assertThat(inner.get()).isEqualTo(1);
    }
}
