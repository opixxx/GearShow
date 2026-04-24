package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.TripoSemaphorePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis 비활성화 상태에서 쓰이는 {@link TripoSemaphorePort} No-op 구현.
 *
 * <p>{@code gearshow.redis.enabled=false} (기본) 환경에서 쓰이며 permit 체크 없이 action 을
 * 즉시 실행한다. 단일 인스턴스·로컬 개발·테스트 환경 한정. {@code WorkflowLockFallbackConfig}
 * 와 동일한 Kafka 대칭 패턴.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "gearshow.redis.enabled",
        havingValue = "false", matchIfMissing = true)
public class NoopTripoSemaphoreAdapter implements TripoSemaphorePort {

    public NoopTripoSemaphoreAdapter() {
        log.warn("Tripo 세마포어 비활성화 — No-op 으로 기동. 프로덕션 다중 인스턴스 운영 시 "
                + "반드시 gearshow.redis.enabled=true 로 설정하세요.");
    }

    @Override
    public <T> T runWithPermit(Duration timeout, Supplier<T> action) {
        return action.get();
    }
}
