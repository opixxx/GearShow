package com.gearshow.backend.platform.idempotency.application.port.out;

import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyRecord;

import java.time.Instant;
import java.util.Optional;

/**
 * API 경계 멱등성 저장소 Outbound Port.
 *
 * <p>{@code idempotency_key} 테이블에 대한 접근을 추상화한다.
 * Kafka Consumer 용 {@link ProcessedMessagePort} 와는 별개의 경로이며,
 * 응답 캐싱과 사용자 소유권 개념이 포함된다.</p>
 */
public interface ApiIdempotencyPort {

    /**
     * 새 키를 {@code IN_PROGRESS} 상태로 원자적으로 저장한다.
     *
     * <p>UNIQUE 제약 위반 시 예외 없이 {@code false} 를 반환하여
     * 트랜잭션 rollback-only 플래그 오염을 회피한다.</p>
     *
     * @return 신규 저장이면 {@code true}, 이미 존재하면 {@code false}
     */
    boolean saveIfAbsent(String idempotencyKey, Long userId, Instant expiresAt);

    /**
     * 키로 조회한다. 소유권 검증은 호출자(Service) 책임이다.
     */
    Optional<ApiIdempotencyRecord> findByKey(String idempotencyKey);

    /**
     * 처리 완료를 기록한다. {@code IN_PROGRESS} 가 아닌 상태에서 호출하면 예외가 발생할 수 있다.
     */
    void markDone(String idempotencyKey, int httpStatus, String responseBody);
}
