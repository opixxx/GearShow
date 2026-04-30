package com.gearshow.backend.showcase.adapter.out.redis;

import com.gearshow.backend.showcase.application.port.out.ModelGenerationDailyQuotaPort;
import com.gearshow.backend.showcase.infrastructure.config.ModelGenerationQuotaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * {@link ModelGenerationDailyQuotaPort} 의 Redisson 기반 구현.
 *
 * <p>키: {@code quota:model-generation:user:{userId}:{KST yyyyMMdd}} — 키 자체에 날짜를 박아
 * 다음 날엔 자연스럽게 새 카운터가 시작된다 (TTL 만료 race 방어).
 * TTL: 다음 KST 자정까지. 매 호출마다 EXPIRE 도 idempotent — INCR 직후 JVM 크래시로 EXPIRE 누락
 * 시 키가 영구화되는 위험을 1회 호출만에 자가복구.</p>
 *
 * <p>Race: Redisson {@code RAtomicLong.incrementAndGet} 이 atomic. 단일 Redis 인스턴스 가정
 * (sentinel/cluster 도입 전).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisModelGenerationDailyQuotaAdapter implements ModelGenerationDailyQuotaPort {

    static final String KEY_PREFIX = "quota:model-generation:user:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedissonClient redissonClient;
    private final ModelGenerationQuotaProperties properties;
    private final Clock clock;

    @Override
    public QuotaResult tryConsume(Long userId) {
        ZonedDateTime nowKst = ZonedDateTime.now(clock.withZone(KST));
        Instant resetAt = nextMidnightKst(nowKst).toInstant();
        String key = buildKey(userId, nowKst.toLocalDate());
        long limit = properties.dailyLimitPerUser();

        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long count = counter.incrementAndGet();
        // EXPIRE 는 매 호출 idempotent — INCR/EXPIRE 사이 크래시 시 다음 호출이 자가복구.
        counter.expire(resetAt);

        if (count > limit) {
            counter.decrementAndGet();
            log.info("3D 일일 quota 초과 — userId: {}, limit: {}, currentCount: {}",
                    userId, limit, limit);
            return new QuotaResult(false, limit, limit, resetAt);
        }
        return new QuotaResult(true, count, limit, resetAt);
    }

    @Override
    public void rollback(Long userId) {
        String key = buildKey(userId, LocalDate.now(clock.withZone(KST)));
        try {
            long after = redissonClient.getAtomicLong(key).decrementAndGet();
            log.info("3D 일일 quota rollback — userId: {}, currentCount: {}", userId, after);
        } catch (RuntimeException e) {
            // rollback 실패는 swallow — 원본 예외 흐름을 가리지 않는다.
            log.warn("3D 일일 quota rollback 실패 (무시) — userId: {}", userId, e);
        }
    }

    private String buildKey(Long userId, LocalDate kstDate) {
        return KEY_PREFIX + userId + ":" + kstDate.format(DATE_FORMAT);
    }

    private ZonedDateTime nextMidnightKst(ZonedDateTime nowKst) {
        return nowKst.toLocalDate().plusDays(1).atStartOfDay(KST);
    }
}
