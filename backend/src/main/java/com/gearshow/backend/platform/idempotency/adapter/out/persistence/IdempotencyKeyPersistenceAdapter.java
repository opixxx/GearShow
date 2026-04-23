package com.gearshow.backend.platform.idempotency.adapter.out.persistence;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
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
 * <p>{@link ApiIdempotencyPort} 의 JPA 구현. {@link IdempotencyKeyJpaEntity} 및
 * {@link IdempotencyKeyJpaRepository} 를 활용한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class IdempotencyKeyPersistenceAdapter implements ApiIdempotencyPort {

    private final IdempotencyKeyJpaRepository repository;

    @Override
    @Transactional
    public boolean saveIfAbsent(String idempotencyKey, Long userId, Instant expiresAt) {
        int inserted = repository.insertIfAbsent(
                idempotencyKey, userId,
                IdempotencyKeyJpaEntity.Status.IN_PROGRESS.name(),
                Instant.now(), expiresAt);
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

    @Override
    @Transactional
    public void markDone(String idempotencyKey, int httpStatus, String responseBody) {
        int updated = repository.markDoneIfInProgress(
                idempotencyKey,
                IdempotencyKeyJpaEntity.Status.IN_PROGRESS.name(),
                IdempotencyKeyJpaEntity.Status.DONE.name(),
                httpStatus, responseBody);
        if (updated == 0) {
            // IN_PROGRESS 이던 레코드가 사라졌거나 이미 DONE 이 됐다 — 상태 전이 경계 위반
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_MISSING_AFTER_ACQUIRE);
        }
    }

    @Override
    @Transactional
    public void discardOnFailure(String idempotencyKey) {
        repository.deleteByIdempotencyKey(idempotencyKey);
    }

    private ApiIdempotencyStatus toApplicationStatus(IdempotencyKeyJpaEntity.Status status) {
        return switch (status) {
            case IN_PROGRESS -> ApiIdempotencyStatus.IN_PROGRESS;
            case DONE -> ApiIdempotencyStatus.DONE;
        };
    }
}
