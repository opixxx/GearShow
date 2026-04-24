package com.gearshow.backend.showcase.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 클라이언트 설정.
 *
 * <p>3D 파이프라인 분산 락({@code workflow:lock:{workflowId}}) 용도로 {@code RedissonClient} 를
 * 등록한다. 설계 §3.4 에 따라 후속 Phase 에서 {@code tripo:semaphore} · {@code poll:delayed-queue}
 * 등이 같은 클라이언트를 공유해 사용할 예정이다.</p>
 *
 * <p><b>조건부 로딩</b>: {@code gearshow.redis.enabled=true} 일 때만 Bean 을 등록한다. 테스트 환경은
 * 이 플래그를 {@code false} 로 두어 전체 경로를 Redis 없이도 기동 가능하게 한다 (Kafka 와 동일 패턴).
 * Redisson 통합 테스트는 Testcontainers 로 Redis 를 기동하고 동적으로 플래그를 켠다.</p>
 *
 * <p><b>전용 클라이언트 (Spring Data Redis 와 분리)</b>: Redisson core 만 사용하고 Redisson Spring
 * Boot starter 는 쓰지 않는다. 이유는 auto-config 가 {@code spring.data.redis.*} 네임스페이스를
 * 요구하는데 우리는 커스텀 플래그({@code gearshow.redis.*}) 로 조건부 로딩을 해야 하기 때문이다.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "gearshow.redis.enabled", havingValue = "true")
public class RedissonConfig {

    @Value("${gearshow.redis.host}")
    private String host;

    @Value("${gearshow.redis.port:6379}")
    private int port;

    /**
     * 단일 Redis 노드용 {@link RedissonClient}. 클러스터/센티넬 구성은 §12 #4 인프라 결정 이후
     * 별도 profile 에서 분기 예정이다.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                // Redisson 기본 connect/command timeout 은 10초. Worker TX1 의 ms 수준 홀드타임과
                // 어울리지 않아 짧게 설정한다.
                .setConnectTimeout(1_000)
                .setTimeout(2_000);
        log.info("Redisson 클라이언트 초기화 — host: {}, port: {}", host, port);
        return Redisson.create(config);
    }
}
