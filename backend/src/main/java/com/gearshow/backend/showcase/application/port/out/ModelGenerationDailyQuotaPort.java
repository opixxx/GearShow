package com.gearshow.backend.showcase.application.port.out;

import java.time.Instant;

/**
 * 사용자별 3D 모델 생성 일일 quota 카운터 포트.
 *
 * <p>구현체는 KST 자정에 자동 초기화되는 atomic 카운터를 제공한다 (Redis INCR + EXPIRE).
 * 본 포트는 비즈니스 정책 (limit 값) 검사와 rollback 만 표현하며, 시각/스토리지 디테일은
 * 어댑터 책임.</p>
 */
public interface ModelGenerationDailyQuotaPort {

    /**
     * 사용자의 오늘 카운트를 1 증가 시도. limit 초과 시 자동 rollback 후
     * {@code allowed=false} 반환.
     *
     * @param userId 사용자 ID
     * @return INCR 결과. {@code allowed=true} 이면 호출자가 후속 작업 진행 가능.
     *         후속 작업이 실패하면 {@link #rollback(Long)} 호출 의무.
     */
    QuotaResult tryConsume(Long userId);

    /**
     * {@link #tryConsume(Long)} 후 후속 작업이 실패한 경우 카운터를 1 감소 시킨다.
     * 호출 자체가 실패해도 swallow + log — 원래 예외 흐름을 가리지 않는다 (어댑터 책임).
     */
    void rollback(Long userId);

    /**
     * quota 시도 결과.
     *
     * @param allowed       limit 내라 호출자가 진행 가능한지
     * @param currentCount  rollback 반영 후 실제 카운트
     * @param limit         적용 중인 일일 quota 상한
     * @param resetAt       quota 초기화 시점 (KST 다음 자정)
     */
    record QuotaResult(boolean allowed, long currentCount, long limit, Instant resetAt) {
    }
}
