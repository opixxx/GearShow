package com.gearshow.backend.showcase.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 클라이언트 설정.
 *
 * <p>3D 파이프라인 분산 락({@code workflow:lock:{workflowId}}) · {@code tripo:semaphore} ·
 * {@code poll:delayed-queue} 등이 같은 클라이언트를 공유한다.</p>
 *
 * <p><b>필수 인프라 (ADR-013)</b>: Redis 는 GearShow 의 필수 의존이다. 부팅 시
 * {@code gearshow.redis.host} 가 비어있거나 Redis 미연결이면 ApplicationContext 부팅이 실패한다
 * (fail-fast). 단일 인스턴스 prod 도 동일하게 Redis 가 필요하다 — daily quota 같은 보안/비용
 * 보호 기능이 단일 인스턴스에서도 정상 작동해야 하기 때문.</p>
 *
 * <p>테스트 환경은 {@code RedisContainerSupport} abstract base class 가 Testcontainers Redis 를
 * 정적 spin-up 으로 공유한다.</p>
 *
 * <p><b>전용 클라이언트 (Spring Data Redis 와 분리)</b>: Redisson core 만 사용하고 Redisson Spring
 * Boot starter 는 쓰지 않는다. {@code spring.data.redis.*} 네임스페이스 충돌 방지 + 명시적
 * config 제어 목적.</p>
 */
@Slf4j
@Configuration
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
