package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.exception.SemaphoreInterruptedException;
import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;
import com.gearshow.backend.showcase.application.port.out.TripoSemaphorePort;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * {@link TripoSemaphorePort} 의 Redisson 기반 구현 (설계 §3.4 {@code tripo:semaphore}, 10 permits).
 *
 * <p>PostConstruct 에서 {@link RSemaphore#trySetPermits(int)} 로 최초 1회만 초기 permits 설정.
 * 이미 다른 인스턴스가 초기화했다면 {@code false} 를 반환하고 자연스럽게 통과. 재시작 후에도 기존
 * 값이 유지된다.</p>
 *
 * <p>{@code tryAcquire(timeout)} 이 false → {@link TripoSemaphoreTimeoutException} 으로 전환.
 * 이 예외는 {@code ModelGenerationRetryableException} 을 상속하므로 Worker 경로에서는
 * {@code @RetryableTopic} 이 자동 backoff 재시도를 수행한다.</p>
 *
 * <p>Redis 가 GearShow 의 필수 인프라 (ADR-013) 이므로 본 어댑터는 무조건 빈으로 등록된다.
 * Noop fallback ({@code NoopTripoSemaphoreAdapter}) 은 ADR-013 으로 폐기됐다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonTripoSemaphoreAdapter implements TripoSemaphorePort {

    static final String SEMAPHORE_KEY = "tripo:semaphore";
    /** 설계 §3.4 — Tripo 동시 요청 10슬롯. */
    private static final int PERMITS = 10;

    private final RedissonClient redissonClient;

    @PostConstruct
    void initSemaphore() {
        RSemaphore semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        boolean initialized = semaphore.trySetPermits(PERMITS);
        log.info("tripo:semaphore 초기화 — permits: {}, 최초 설정: {}", PERMITS, initialized);
    }

    @Override
    public <T> T runWithPermit(Duration timeout, Supplier<T> action) {
        RSemaphore semaphore = redissonClient.getSemaphore(SEMAPHORE_KEY);
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 인터럽트는 "대기 시간 초과" 가 아니라 "스레드 종료 요청" — Retryable 계열과 분리해
            // 상위가 루프 종료로 해석하도록 SemaphoreInterruptedException 으로 던진다.
            Thread.currentThread().interrupt();
            throw new SemaphoreInterruptedException();
        }
        if (!acquired) {
            log.warn("tripo:semaphore 획득 실패 — timeout: {}ms", timeout.toMillis());
            throw new TripoSemaphoreTimeoutException();
        }
        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }
}
