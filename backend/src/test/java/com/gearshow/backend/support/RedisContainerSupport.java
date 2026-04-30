package com.gearshow.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis 가 필수가 된 GearShow 인프라 정책 (ADR-013) 에 따라, 모든 {@code @SpringBootTest} 가
 * RedissonClient 부팅을 의존하게 된다. 본 abstract base class 는 Testcontainers Redis 를
 * 정적 1회 spin-up 으로 모든 테스트 인스턴스가 공유하게 한다.
 *
 * <p><b>사용법</b>: 통합 테스트가 본 클래스를 상속하면 자동으로 {@code gearshow.redis.host} /
 * {@code gearshow.redis.port} 가 컨테이너 좌표로 주입된다. 자체 {@code @SpringBootTest}/
 * {@code @ActiveProfiles}/추가 {@code @DynamicPropertySource} 는 상속 클래스가 자유롭게 선언.</p>
 *
 * <p><b>왜 정적 GenericContainer 인가</b>: JUnit Jupiter 의 {@code @Container} 는 인스턴스/클래스
 * 단위 lifecycle 만 제공한다. 정적 필드 + 정적 init block 으로 JVM 안에서 1회만 spin-up 시키면
 * 모든 통합 테스트 클래스가 같은 컨테이너를 공유 → IT 수십 개에서 컨테이너 spin-up 비용을 1회로
 * 압축한다. {@code @Testcontainers} 어노테이션도 불필요 (lifecycle 직접 제어).</p>
 *
 * <p><b>키 격리</b>: 모든 통합 테스트가 같은 Redis 인스턴스를 공유하므로 키 충돌 가능성에 주의.
 * Redisson 은 prefix 없는 raw key 를 쓰므로, 테스트마다 고유 prefix (예: {@code workflowId})
 * 를 부여하거나 {@code @BeforeEach} 에서 {@code FLUSHDB} 로 초기화하는 방식을 추천.</p>
 */
public abstract class RedisContainerSupport {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    protected static final GenericContainer<?> REDIS;

    static {
        REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT);
        REDIS.start();
        // JVM 종료 시 자동 정리 (Testcontainers Ryuk).
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("gearshow.redis.host", REDIS::getHost);
        registry.add("gearshow.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }
}
