package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;
import com.gearshow.backend.showcase.application.port.out.TripoSemaphorePort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedissonTripoSemaphoreAdapter} 실제 Redis 기반 통합 테스트.
 *
 * <p>검증:</p>
 * <ul>
 *   <li>permit 획득 후 action 실행</li>
 *   <li>10 permits 초과 요청은 timeout → {@link TripoSemaphoreTimeoutException}</li>
 *   <li>release 후 재획득 가능</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("RedissonTripoSemaphoreAdapter 통합")
class RedissonTripoSemaphoreAdapterIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        // 테스트가 세마포어 어댑터만 단독 사용하도록 Poller 루프는 끈다.
        registry.add("app.workflow-polling.scheduler-enabled", () -> "false");
    }

    @Autowired
    private TripoSemaphorePort semaphorePort;

    @Test
    @DisplayName("단일 호출: permit 획득 후 action 실행, 반환값 그대로 전달")
    void singleCall_runsActionAndReturns() {
        String result = semaphorePort.runWithPermit(
                Duration.ofSeconds(1), () -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("10 permits 모두 보유 중이면 11번째 요청은 짧은 timeout 으로 TripoSemaphoreTimeoutException")
    void allPermitsHeld_eleventhTimesOut() throws Exception {
        int permits = 10;
        CountDownLatch allEntered = new CountDownLatch(permits);
        CountDownLatch mayExit = new CountDownLatch(1);
        List<Thread> holders = new ArrayList<>();
        try {
            for (int i = 0; i < permits; i++) {
                Thread t = new Thread(() ->
                        semaphorePort.runWithPermit(Duration.ofSeconds(5), () -> {
                            allEntered.countDown();
                            try {
                                mayExit.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return null;
                        }));
                t.start();
                holders.add(t);
            }
            assertThat(allEntered.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Throwable> extra = new AtomicReference<>();
            Thread eleventh = new Thread(() -> {
                try {
                    semaphorePort.runWithPermit(Duration.ofMillis(200), () -> null);
                } catch (Throwable e) {
                    extra.set(e);
                }
            });
            eleventh.start();
            eleventh.join(5_000);

            assertThat(extra.get()).isInstanceOf(TripoSemaphoreTimeoutException.class);
        } finally {
            mayExit.countDown();
            for (Thread t : holders) {
                t.join(5_000);
            }
        }
    }

    @Test
    @DisplayName("release 후 재획득 가능 — 직렬로 여러 번 호출해도 소진되지 않는다")
    void releaseThenReacquire() {
        AtomicInteger count = new AtomicInteger();
        for (int i = 0; i < 20; i++) {
            semaphorePort.runWithPermit(Duration.ofSeconds(1), () -> {
                count.incrementAndGet();
                return null;
            });
        }
        assertThat(count.get()).isEqualTo(20);
    }
}
