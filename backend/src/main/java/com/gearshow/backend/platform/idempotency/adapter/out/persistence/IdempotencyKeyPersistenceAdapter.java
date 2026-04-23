package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyRecord;
import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyStatus;
import com.gearshow.backend.platform.idempotency.application.port.out.ApiIdempotencyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * API 경계 멱등성 키 Persistence Adapter.
 *
 * <p>{@link ApiIdempotencyPort} 의 JPA 구현. {@link IdempotencyKeyJpaEntity} 및 {@link IdempotencyKeyJpaRepository}
 * 를 활용한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyKeyPersistenceAdapter implements ApiIdempotencyPort {

    private final IdempotencyKeyJpaRepository repository;

    @Override
    @Transactional
    public boolean saveIfAbsent(String idempotencyKey, Long userId, Instant expiresAt) {
        int inserted = repository.insertIfAbsent(idempotencyKey, userId, Instant.now(), expiresAt);
        return inserted == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApiIdempotencyRecord> findByKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(e -> new ApiIdempotencyRecord(
                        e.getUserId(), toApplicationStatus(e.getStatus()),
                        e.getHttpStatus(), e.getResponseBody()));
    }

    private ApiIdempotencyStatus toApplicationStatus(IdempotencyKeyJpaEntity.Status status) {
        return switch (status) {
            case IN_PROGRESS -> ApiIdempotencyStatus.IN_PROGRESS;
            case DONE -> ApiIdempotencyStatus.DONE;
        };
    }

    @Override
    @Transactional
    public void markDone(String idempotencyKey, int httpStatus, String responseBody) {
        IdempotencyKeyJpaEntity entity = repository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "markDone 호출 대상 멱등성 키가 존재하지 않습니다. key=" + idempotencyKey));
        entity.markDone(httpStatus, responseBody);
        // dirty checking 으로 자동 UPDATE
    }
}
