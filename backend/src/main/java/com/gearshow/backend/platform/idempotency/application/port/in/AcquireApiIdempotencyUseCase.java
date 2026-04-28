package com.gearshow.backend.platform.idempotency.application.port.in;

import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyAcquireResult;

import java.time.Duration;

/**
 * API 경계 멱등성 획득/완료 유스케이스.
 *
 * <p>{@code Idempotency-Key} 헤더 기반 요청 dedup 및 응답 캐싱을 담당한다.
 * Kafka Consumer 용 {@link MessageIdempotencyUseCase} 와는 별개의 경로이다.</p>
 */
public interface AcquireApiIdempotencyUseCase {

    /**
     * 멱등성 키를 획득한다.
     *
     * <ul>
     *   <li>처음 보는 키 → {@link ApiIdempotencyAcquireResult.New} 반환. 호출자는 비즈니스 로직을 진행한다.</li>
     *   <li>이미 완료된 키 (소유자 일치) → {@link ApiIdempotencyAcquireResult.Cached} 반환. 호출자는 저장된 응답을 반환한다.</li>
     *   <li>아직 처리 중인 키 → {@code ApiIdempotencyConflictException} (409) 를 던진다.</li>
     *   <li>다른 사용자의 키 → {@code ApiIdempotencyOwnershipMismatchException} (403) 을 던진다.</li>
     * </ul>
     */
    ApiIdempotencyAcquireResult acquire(String idempotencyKey, Long userId, Duration ttl);

    /**
     * 처리 완료를 기록한다. 이후 같은 키로 오는 요청은 캐싱된 응답을 반환받는다.
     */
    void markDone(String idempotencyKey, int httpStatus, String responseBody);

    /**
     * 비즈니스 로직 실패 시 {@code IN_PROGRESS} 레코드를 제거한다. 같은 키로 재시도가 가능하도록
     * 보상 삭제하여 좀비 상태를 방지한다.
     */
    void discardOnFailure(String idempotencyKey);
}
