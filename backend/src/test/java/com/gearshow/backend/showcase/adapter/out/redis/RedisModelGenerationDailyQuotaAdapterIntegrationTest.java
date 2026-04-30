package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationDailyQuotaPort;
import com.gearshow.backend.showcase.application.port.out.ModelGenerationDailyQuotaPort.QuotaResult;
import com.gearshow.backend.support.TestOAuthConfig;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RedisModelGenerationDailyQuotaAdapter} 실제 Redis 통합 테스트.
 *
 * <p>본 IT 는 {@code TestInfraConfig} 의 mock {@link ModelGenerationDailyQuotaPort} 빈을 의도적으로
 * 사용하지 않는다 — 실제 Redis Adapter 동작 (INCR + EXPIRE + DECR) 검증이 본 IT 의 목적.
 * 자체 Testcontainers Redis + 동적 좌표 주입.</p>
 *
 * <p>키 격리: 시나리오마다 고유 userId (System.nanoTime()) 사용 + {@code @AfterEach} 로
 * 사용한 키 정리.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestOAuthConfig.class)
@Testcontainers
@DisplayName("RedisModelGenerationDailyQuotaAdapter 통합")
class RedisModelGenerationDailyQuotaAdapterIntegrationTest {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        // 본 IT 는 실제 Redis Adapter 검증 — TestInfraConfig 의 mock 우회 위해 limit 명시.
        registry.add("app.model-generation.daily-limit-per-user", () -> "3");
        // 폴링 루프 끔.
        registry.add("app.workflow-polling.scheduler-enabled", () -> "false");
    }

    @Autowired
    private RedisModelGenerationDailyQuotaAdapter adapter;

    @Autowired
    private RedissonClient redissonClient;

    private Long userId;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // 각 시나리오마다 unique userId — Redis 키 충돌 방지.
        userId = System.nanoTime();
    }

    @AfterEach
    void cleanUp() {
        // 본 시나리오에서 만든 모든 quota 키 삭제 (다음 IT 에 영향 안 가게).
        redissonClient.getKeys()
                .deleteByPattern(RedisModelGenerationDailyQuotaAdapter.KEY_PREFIX + userId + ":*");
    }

    @Test
    @DisplayName("첫 호출 시 count=1, allowed=true, TTL 셋")
    void firstCall_succeeds_andSetsTtl() {
        QuotaResult result = adapter.tryConsume(userId);

        assertThat(result.allowed()).isTrue();
        assertThat(result.currentCount()).isEqualTo(1L);
        assertThat(result.limit()).isEqualTo(3L);
        // resetAt 은 미래 시점 (다음 KST 자정).
        assertThat(result.resetAt()).isAfter(Instant.now());
        // TTL 이 셋되어 있어야 함 (양수).
        long ttl = redissonClient.getKeys()
                .remainTimeToLive(RedisModelGenerationDailyQuotaAdapter.KEY_PREFIX
                        + userId + ":" + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));
        assertThat(ttl).isPositive();
    }

    @Test
    @DisplayName("limit (3) 까지 호출은 모두 통과")
    void upToLimit_allCallsAllowed() {
        for (int i = 1; i <= 3; i++) {
            QuotaResult r = adapter.tryConsume(userId);
            assertThat(r.allowed()).isTrue();
            assertThat(r.currentCount()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("limit 초과 호출은 거부 + 카운트 limit 유지 (rollback 동작)")
    void exceedLimit_isRejected_andCountStaysAtLimit() {
        // 3회 통과
        for (int i = 0; i < 3; i++) {
            adapter.tryConsume(userId);
        }
        // 4회째 거부
        QuotaResult denied = adapter.tryConsume(userId);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.currentCount()).isEqualTo(3L);
        assertThat(denied.limit()).isEqualTo(3L);

        // 5회째도 거부 + 카운트 여전히 3 (DECR rollback 정상 동작)
        QuotaResult denied2 = adapter.tryConsume(userId);
        assertThat(denied2.allowed()).isFalse();
        assertThat(denied2.currentCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("rollback 호출 시 카운트 1 감소")
    void rollback_decrementsCount() {
        adapter.tryConsume(userId);  // count=1
        adapter.tryConsume(userId);  // count=2

        adapter.rollback(userId);

        // 다음 INCR 결과로 카운트 확인 — 2가 되어야 (rollback 후 1 → INCR → 2)
        QuotaResult result = adapter.tryConsume(userId);
        assertThat(result.currentCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("다른 사용자는 독립 카운트")
    void differentUsers_haveIndependentCounts() {
        Long otherUserId = userId + 1;
        try {
            adapter.tryConsume(userId);
            adapter.tryConsume(userId);
            QuotaResult other = adapter.tryConsume(otherUserId);

            assertThat(other.currentCount()).isEqualTo(1L);
            assertThat(other.allowed()).isTrue();
        } finally {
            redissonClient.getKeys()
                    .deleteByPattern(RedisModelGenerationDailyQuotaAdapter.KEY_PREFIX
                            + otherUserId + ":*");
        }
    }

    @Test
    @DisplayName("rollback 호출이 실패 (Redis 키 없음 → DECR 0 으로 떨어짐) 해도 swallow")
    void rollback_neverThrows() {
        // tryConsume 호출 없이 바로 rollback — DECR 시 키가 없으면 Redisson 이 0 → -1 로 만들거나
        // 자체 처리. swallow 동작 확인.
        Duration timeout = Duration.ofSeconds(2);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(timeout,
                () -> adapter.rollback(userId));
    }
}
