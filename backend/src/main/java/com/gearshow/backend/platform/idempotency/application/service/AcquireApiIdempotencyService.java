package com.gearshow.backend.platform.idempotency.application.service;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;
import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyAcquireResult;
import com.gearshow.backend.platform.idempotency.application.dto.ApiIdempotencyRecord;
import com.gearshow.backend.platform.idempotency.application.exception.ApiIdempotencyConflictException;
import com.gearshow.backend.platform.idempotency.application.exception.ApiIdempotencyOwnershipMismatchException;
import com.gearshow.backend.platform.idempotency.application.port.in.AcquireApiIdempotencyUseCase;
import com.gearshow.backend.platform.idempotency.application.port.out.ApiIdempotencyPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * API 경계 멱등성 유스케이스 구현.
 *
 * <p>UNIQUE 제약을 활용한 {@code INSERT IGNORE} 로 원자적으로 선점하며, 선점 실패 시
 * 이미 존재하는 레코드의 소유자/상태에 따라 분기한다. 소유자 불일치는 IDOR 방지를 위해 즉시 차단한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcquireApiIdempotencyService implements AcquireApiIdempotencyUseCase {

    private final ApiIdempotencyPort apiIdempotencyPort;

    @Override
    public ApiIdempotencyAcquireResult acquire(String idempotencyKey, Long userId, Duration ttl) {
        if (tryInsertNew(idempotencyKey, userId, ttl)) {
            return new ApiIdempotencyAcquireResult.New();
        }
        return resolveExisting(idempotencyKey, userId);
    }

    @Override
    public void markDone(String idempotencyKey, int httpStatus, String responseBody) {
        apiIdempotencyPort.markDone(idempotencyKey, httpStatus, responseBody);
    }

    @Override
    public void discardOnFailure(String idempotencyKey) {
        apiIdempotencyPort.discardOnFailure(idempotencyKey);
    }

    private boolean tryInsertNew(String idempotencyKey, Long userId, Duration ttl) {
        Instant expiresAt = Instant.now().plus(ttl);
        return apiIdempotencyPort.saveIfAbsent(idempotencyKey, userId, expiresAt);
    }

    private ApiIdempotencyAcquireResult resolveExisting(String idempotencyKey, Long userId) {
        ApiIdempotencyRecord existing = apiIdempotencyPort.findByKey(idempotencyKey)
                .orElseThrow(() -> new CustomException(ErrorCode.IDEMPOTENCY_KEY_MISSING_AFTER_ACQUIRE));

        verifyOwnership(existing, userId, idempotencyKey);

        if (existing.isDone()) {
            log.info("멱등성 키 캐시 적중 — key={}, httpStatus={}", idempotencyKey, existing.httpStatus());
            return new ApiIdempotencyAcquireResult.Cached(
                    existing.httpStatus(), existing.responseBody());
        }

        log.info("멱등성 키 IN_PROGRESS 재도달 — key={}", idempotencyKey);
        throw new ApiIdempotencyConflictException();
    }

    private void verifyOwnership(ApiIdempotencyRecord existing, Long userId, String idempotencyKey) {
        if (!existing.userId().equals(userId)) {
            log.warn("멱등성 키 소유권 불일치 — key={}, ownerUserId={}, requestUserId={}",
                    idempotencyKey, existing.userId(), userId);
            throw new ApiIdempotencyOwnershipMismatchException();
        }
    }
}
