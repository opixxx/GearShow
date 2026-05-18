package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.AdmissionDrainLockPort;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link AdmissionDrainLockPort} 의 Redisson {@code RLock} 구현 (ADR-025, 키
 * {@code tripo:queue:drain-lock}).
 *
 * <p>{@code tryLock(0, lease, ...)} — 대기 0(비대기), lease 자동 해제. 보유 인스턴스가
 * 사망해도 lease 만료로 자동 회수되어 누수가 없다. Redis 가 필수 인프라(ADR-013) 이므로
 * 무조건 빈으로 등록된다.</p>
 */
@Slf4j
@Component
public class RedissonAdmissionDrainLockAdapter implements AdmissionDrainLockPort {

    static final String LOCK_KEY = "tripo:queue:drain-lock";

    /** Redisson RLock 은 재진입 가능한 stateless 핸들 — 생성자에서 1회 캐시(큐 어댑터와 동일 패턴). */
    private final RLock lock;

    public RedissonAdmissionDrainLockAdapter(RedissonClient redissonClient) {
        this.lock = redissonClient.getLock(LOCK_KEY);
    }

    @Override
    public boolean tryLock(Duration lease) {
        try {
            return lock.tryLock(0, lease.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("drain-lock 획득 중 인터럽트 — skip");
            return false;
        }
    }

    @Override
    public void unlock() {
        // lease 선만료 후 다른 인스턴스가 잡았을 수 있으므로 보유 스레드만 해제(IllegalMonitorState 방지).
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
