package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.TripoAdmissionQueuePort;
import com.gearshow.backend.support.TestInfraConfig;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedissonTripoAdmissionQueueAdapter} 실제 Redis 기반 통합 테스트 (ADR-025).
 *
 * <p>ZSET 의 ZADD-NX({@code addIfAbsent}) / ZPOPMIN({@code pollFirst}) atomic 시맨틱은 mock 으로
 * 대체할 수 없으므로 Testcontainers 실 Redis 로 검증한다 (EXEC_PLAN §6).</p>
 *
 * <p>검증: parkIfAbsent 신규/중복(최초 score 유지) · pollOldest FIFO · 빈 큐 · contains/size.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestOAuthConfig.class, TestInfraConfig.class})
@Testcontainers
@DisplayName("RedissonTripoAdmissionQueueAdapter 통합")
class RedissonTripoAdmissionQueueAdapterIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        // 자동 소비자(Poller / drainer / Reconcile)가 테스트 ZSET 을 가로채지 않도록 끈다.
        registry.add("app.workflow-polling.scheduler-enabled", () -> "false");
        registry.add("app.model-generation.admission.enabled", () -> "false");
        registry.add("app.reconcile.enabled", () -> "false");
    }

    @Autowired
    private TripoAdmissionQueuePort admissionQueue;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void clearQueue() {
        // 모든 통합 테스트가 같은 Redis 를 공유하므로 고정 키를 매 테스트 초기화한다.
        redissonClient.getScoredSortedSet(RedissonTripoAdmissionQueueAdapter.QUEUE_KEY).delete();
    }

    @Test
    @DisplayName("parkIfAbsent — 신규는 true, size/contains 반영")
    void parkIfAbsent_new() {
        boolean added = admissionQueue.parkIfAbsent(7_001L, 1_000L);

        assertThat(added).isTrue();
        assertThat(admissionQueue.size()).isEqualTo(1);
        assertThat(admissionQueue.contains(7_001L)).isTrue();
        assertThat(admissionQueue.contains(9_999L)).isFalse();
    }

    @Test
    @DisplayName("parkIfAbsent — 중복은 false, size 불변 (최초 score 유지로 FIFO 보존)")
    void parkIfAbsent_duplicate() {
        assertThat(admissionQueue.parkIfAbsent(7_002L, 1_000L)).isTrue();
        boolean second = admissionQueue.parkIfAbsent(7_002L, 9_000L);  // 더 늦은 score

        assertThat(second).isFalse();
        assertThat(admissionQueue.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("pollOldest — score 오름차순(FIFO) 으로 방출, 제거됨")
    void pollOldest_fifo() {
        admissionQueue.parkIfAbsent(8_003L, 3_000L);
        admissionQueue.parkIfAbsent(8_001L, 1_000L);
        admissionQueue.parkIfAbsent(8_002L, 2_000L);

        assertThat(admissionQueue.pollOldest()).contains(8_001L);
        assertThat(admissionQueue.pollOldest()).contains(8_002L);
        assertThat(admissionQueue.pollOldest()).contains(8_003L);
        assertThat(admissionQueue.size()).isZero();
    }

    @Test
    @DisplayName("pollOldest — 빈 큐는 Optional.empty")
    void pollOldest_empty() {
        assertThat(admissionQueue.pollOldest()).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("중복 park 후에도 최초 대기 순서가 유지된다")
    void duplicatePark_preservesFifoPosition() {
        admissionQueue.parkIfAbsent(8_101L, 1_000L);
        admissionQueue.parkIfAbsent(8_102L, 2_000L);
        // 8_101 을 더 늦은 score 로 재park 시도 — 무시되어 여전히 먼저 나와야 한다.
        admissionQueue.parkIfAbsent(8_101L, 9_000L);

        assertThat(admissionQueue.pollOldest()).contains(8_101L);
        assertThat(admissionQueue.pollOldest()).contains(8_102L);
    }
}
