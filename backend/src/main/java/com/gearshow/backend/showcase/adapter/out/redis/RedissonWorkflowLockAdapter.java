package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.WorkflowLockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * {@link WorkflowLockPort} 의 Redisson 기반 구현.
 *
 * <p>설계 §3.4 에 따라 키 스페이스는 {@code workflow:lock:{workflowId}} 로 고정하고, lease TTL
 * 을 10초로 설정해 Redisson watchdog 이 자동 연장한다. Worker 크래시 시 watchdog 이 멈춰 lease
 * 가 만료되며, Reconcile 이 stuck 을 감지하기 전에 정상 해제된다.</p>
 *
 * <p><b>대기 없음 정책</b>: {@code tryLock(0, leaseTime, ...)} 로 즉시 시도하고 실패 시
 * {@link WorkflowLockBusyException} 을 던진다. 설계 §6.6 "Worker 가 쥐고 있으면 Reconcile 이
 * 자연스럽게 대기/skip" 원칙을 반영 — 호출자가 경합 상황에서 블로킹되지 않도록 한다.</p>
 *
 * <p>Redis 가 GearShow 의 필수 인프라 (ADR-013) 이므로 본 어댑터는 무조건 빈으로 등록된다.
 * in-memory fallback (이전 {@code WorkflowLockFallbackConfig}) 은 ADR-013 으로 폐기됐다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonWorkflowLockAdapter implements WorkflowLockPort {

    private static final String LOCK_KEY_PREFIX = "workflow:lock:";
    /** lease TTL. Redisson watchdog 이 이 값의 1/3 간격으로 자동 연장한다. */
    private static final long LEASE_TIME_MS = 10_000L;

    private final RedissonClient redissonClient;

    @Override
    public void withLock(Long workflowId, LockedAction action) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + workflowId);
        boolean acquired;
        try {
            // waitTime=0: 즉시 시도, 실패 시 busy. leaseTime=10s: watchdog 자동 연장 대상.
            acquired = lock.tryLock(0L, LEASE_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowLockBusyException(workflowId);
        }
        if (!acquired) {
            log.info("workflow 락 획득 실패 (busy) — workflowId: {}", workflowId);
            throw new WorkflowLockBusyException(workflowId);
        }
        try {
            action.run();
        } finally {
            // 현재 스레드가 보유 중인 경우에만 해제 — watchdog 만료로 이미 풀렸을 수도 있다.
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
