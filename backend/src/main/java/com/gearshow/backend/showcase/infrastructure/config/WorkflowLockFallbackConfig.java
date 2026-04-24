package com.gearshow.backend.showcase.infrastructure.config;

import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 비활성화 상태에서 기동될 때 쓰이는 {@link WorkflowLockPort} 기본 구현.
 *
 * <p>{@code gearshow.redis.enabled=false} (또는 누락) 이면 Redisson 어댑터가 Bean 으로 등록되지
 * 않아 의존성 해결이 실패한다. Kafka 와 대칭되는 패턴 — "락 없이 단일 인스턴스로만 운영" 을
 * 명시적으로 가정할 때 No-op 을 제공한다.</p>
 *
 * <p><b>운영 경고</b>: 이 No-op 으로 기동되면 Worker 가 여러 인스턴스에서 동시에 TX1/TX2 를
 * 시도할 경우 ADR-012 조건부 UPDATE 외의 방어가 사라진다. {@code log.warn} 으로 기동 시점에
 * 분명히 알려준다 — Redis 환경변수 누락이 조용히 프로덕션으로 흘러가는 것을 방지.</p>
 *
 * <p>통합 테스트({@code RedissonWorkflowLockAdapterIntegrationTest}) 는
 * {@code @DynamicPropertySource} 로 {@code enabled=true} 를 덮어써 이 Bean 이 아닌 실제
 * Redisson 어댑터가 로드되게 한다.</p>
 */
@Slf4j
@Configuration
public class WorkflowLockFallbackConfig {

    @Bean
    @ConditionalOnProperty(name = "gearshow.redis.enabled",
            havingValue = "false", matchIfMissing = true)
    public WorkflowLockPort noopWorkflowLockPort() {
        log.warn("Redis 분산 락 비활성화 — WorkflowLockPort 를 No-op 으로 기동합니다. "
                + "프로덕션에서 다중 인스턴스 운영 시 반드시 gearshow.redis.enabled=true 로 설정하세요.");
        return (workflowId, action) -> action.run();
    }
}
