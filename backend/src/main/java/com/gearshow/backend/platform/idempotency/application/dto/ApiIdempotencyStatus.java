package com.gearshow.backend.platform.idempotency.application.dto;

/**
 * API 경계 멱등성 레코드의 상태.
 *
 * <p>Application 계층에서 다루는 도메인 개념. Persistence 측 엔티티 enum 과는 1:1 대응되지만
 * 계층 경계를 유지하기 위해 별도로 정의한다 (헥사고날 — application 은 adapter 를 참조하지 않는다).</p>
 */
public enum ApiIdempotencyStatus {

    IN_PROGRESS,
    DONE
}
