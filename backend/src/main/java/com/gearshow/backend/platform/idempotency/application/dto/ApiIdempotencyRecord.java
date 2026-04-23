package com.gearshow.backend.platform.idempotency.application.dto;

/**
 * 저장된 API 멱등성 키 레코드의 불변 스냅샷.
 *
 * @param userId        해당 키를 처음 등록한 사용자 ID (소유권 검증 대상)
 * @param status        현재 상태
 * @param httpStatus    DONE 상태에서 캐싱된 HTTP 상태 코드 (IN_PROGRESS 시 {@code null})
 * @param responseBody  DONE 상태에서 캐싱된 응답 본문 JSON (IN_PROGRESS 시 {@code null})
 */
public record ApiIdempotencyRecord(
        Long userId,
        ApiIdempotencyStatus status,
        Integer httpStatus,
        String responseBody
) {
    public boolean isDone() {
        return status == ApiIdempotencyStatus.DONE;
    }

    public boolean isInProgress() {
        return status == ApiIdempotencyStatus.IN_PROGRESS;
    }
}
