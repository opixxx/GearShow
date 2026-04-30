package com.gearshow.backend.showcase.application.port.out;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 사용자별 3D 모델 생성 일일 quota 카운터 포트.
 *
 * <p>구현체는 atomic 카운터 + 자동 만료 (KST 자정 등) 를 제공한다. 본 포트는 비즈니스 정책
 * (limit 검사) + 토큰 기반 rollback 만 표현하며, 시각/스토리지 디테일은 어댑터 책임.</p>
 *
 * <p><b>토큰 패턴 (자정 경계 race 방어)</b>: {@link #tryConsume(Long)} 이 발급한 {@link QuotaToken}
 * 을 호출자가 보관해 {@link #rollback(QuotaToken)} 에 그대로 넘긴다. 자정을 넘어가도 INCR 한 정확한
 * 키에 DECR 이 가도록 보장.</p>
 */
public interface ModelGenerationDailyQuotaPort {

    /**
     * 사용자의 오늘 카운트를 1 증가 시도. limit 초과 시 INCR 분만큼 즉시 자가 보정 후
     * {@code allowed=false} 반환.
     *
     * @param userId 사용자 ID
     * @return INCR 결과. {@code allowed=true} 이면 호출자가 후속 작업 진행 가능. 후속 작업이
     *         실패하면 {@link #rollback(QuotaToken)} 에 {@link QuotaResult#token()} 을 전달.
     */
    QuotaResult tryConsume(Long userId);

    /**
     * {@link #tryConsume(Long)} 후 후속 작업이 실패한 경우 카운터를 1 감소 시킨다.
     * 토큰의 날짜를 그대로 사용하므로 자정 경계 race 시에도 정확한 키에 DECR 이 적용된다.
     * 호출 자체가 실패해도 swallow + log — 원래 예외 흐름을 가리지 않는다.
     */
    void rollback(QuotaToken token);

    /**
     * quota 시도 결과.
     *
     * @param allowed       limit 내라 호출자가 진행 가능한지
     * @param currentCount  자가 보정 반영 후 실제 카운트 (atomic DECR 반환값 — 동시성 하에서도 정확)
     * @param limit         적용 중인 일일 quota 상한
     * @param resetAt       quota 초기화 시점 (KST 다음 자정)
     * @param token         {@link #rollback(QuotaToken)} 호출 시 전달할 토큰. INCR 키와 1:1.
     */
    record QuotaResult(boolean allowed, long currentCount, long limit, Instant resetAt,
                       QuotaToken token) {
    }

    /**
     * INCR 시점의 키 식별자. 호출자가 보관하다 rollback 에 그대로 전달한다.
     * 어댑터가 같은 키에 DECR 이 가도록 보장하기 위한 토큰 — 자정 경계/날짜 변경 race 방어.
     *
     * @param userId   사용자 ID
     * @param kstDate  INCR 한 키의 KST 날짜 (어댑터가 키 재구성에 사용)
     */
    record QuotaToken(Long userId, LocalDate kstDate) {
    }
}
