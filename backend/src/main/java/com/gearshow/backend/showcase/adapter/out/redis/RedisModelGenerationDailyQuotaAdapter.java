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
import java.time.format.DateTimeFormatter;

/**
 * {@link ModelGenerationDailyQuotaPort} 의 Redisson 기반 구현.
 *
 * <p>키: {@code quota:model-generation:user:{userId}:{KST yyyyMMdd}} — 키 자체에 날짜를 박아
 * 다음 날엔 자연스럽게 새 카운터가 시작된다.
 * TTL: 다음 KST 자정까지. {@code tryConsume}/{@code rollback} 모두 매 호출 EXPIRE 도 idempotent
 * 적용 — INCR/DECR 직후 JVM 크래시 또는 Redis 재시작으로 EXPIRE 누락 시 키가 영구화 (또는 음수 영구
 * 잔존) 되는 위험을 1회 호출만에 자가복구.</p>
 *
 * <p><b>저장 정책 (KST 자정 초기화)</b> 은 본 어댑터의 결정이며 비즈니스 규칙이 아니다. 다른 quota
 * 어댑터 구현 (예: SQL 카운터 + 사용자별 timezone 윈도우) 은 자유롭게 다른 zone/주기를 선택할 수
 * 있도록 포트는 {@code Instant resetAt} 으로 zone-agnostic 하게 노출한다.</p>
 *
 * <p>Race: Redisson {@code RAtomicLong.incrementAndGet} 이 atomic. 자정 경계 rollback 은 토큰의
 * {@link QuotaToken#kstDate()} 를 그대로 사용해 INCR 한 키와 동일 키에 DECR 이 가도록 보장.
 * 단일 Redis 인스턴스 가정 (sentinel/cluster 도입 전).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisModelGenerationDailyQuotaAdapter implements ModelGenerationDailyQuotaPort {

    static final String KEY_PREFIX = "quota:model-generation:user:";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedissonClient redissonClient;
    private final ModelGenerationQuotaProperties properties;
    private final Clock clock;

    @Override
    public QuotaResult tryConsume(Long userId) {
        LocalDate today = todayKst();
        Instant resetAt = nextMidnightKstInstant(today);
        QuotaToken token = new QuotaToken(userId, today);
        String key = buildKey(token);
        long limit = properties.dailyLimitPerUser();

        RAtomicLong counter = redissonClient.getAtomicLong(key);
        long count = counter.incrementAndGet();
        // EXPIRE 는 매 호출 idempotent — INCR 직후 JVM 크래시 시 다음 호출이 자가복구.
        counter.expire(resetAt);

        if (count > limit) {
            long after = counter.decrementAndGet();
            log.info("3D 일일 quota 초과 — userId: {}, limit: {}, observed: {}, currentCount: {}",
                    userId, limit, count, after);
            return new QuotaResult(false, after, limit, resetAt, token);
        }
        return new QuotaResult(true, count, limit, resetAt, token);
    }

    @Override
    public void rollback(QuotaToken token) {
        if (token == null) {
            log.warn("3D 일일 quota rollback 호출에 token 이 null — skip");
            return;
        }
        String key = buildKey(token);
        Instant resetAt = nextMidnightKstInstant(token.kstDate());
        try {
            RAtomicLong counter = redissonClient.getAtomicLong(key);
            long after = counter.decrementAndGet();
            // tryConsume 와 동일한 self-heal — Redis 재시작으로 키가 새로 생성된 경우에도 TTL 보장.
            counter.expire(resetAt);
            log.info("3D 일일 quota rollback — userId: {}, kstDate: {}, currentCount: {}",
                    token.userId(), token.kstDate(), after);
        } catch (RuntimeException e) {
            // rollback 실패는 swallow — 원본 예외 흐름을 가리지 않는다.
            log.warn("3D 일일 quota rollback 실패 (무시) — userId: {}, kstDate: {}",
                    token.userId(), token.kstDate(), e);
        }
    }

    private LocalDate todayKst() {
        return LocalDate.now(clock.withZone(KST));
    }

    private Instant nextMidnightKstInstant(LocalDate kstDate) {
        return kstDate.plusDays(1).atStartOfDay(KST).toInstant();
    }

    private String buildKey(QuotaToken token) {
        return KEY_PREFIX + token.userId() + ":" + token.kstDate().format(DATE_FORMAT);
    }
}
