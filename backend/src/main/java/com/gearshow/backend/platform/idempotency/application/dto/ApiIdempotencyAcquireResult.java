package com.gearshow.backend.platform.idempotency.application.dto;

/**
 * {@code acquire} 호출 결과. 두 가지 중 하나:
 *
 * <ul>
 *   <li>{@link New} — 처음 보는 키. 호출자는 비즈니스 로직을 실행하고 {@code markDone} 을 호출해야 한다.</li>
 *   <li>{@link Cached} — 이미 완료된 키. 호출자는 저장된 응답을 그대로 반환해야 한다.</li>
 * </ul>
 *
 * <p>{@code IN_PROGRESS} 재도달이나 소유권 불일치는 예외로 전파되어 이 타입에는 나타나지 않는다.</p>
 */
public sealed interface ApiIdempotencyAcquireResult {

    /** 신규 키. 호출자는 비즈니스 로직을 진행한다. */
    record New() implements ApiIdempotencyAcquireResult {
    }

    /** 이미 완료된 키. 저장된 응답을 그대로 반환한다. */
    record Cached(int httpStatus, String responseBody) implements ApiIdempotencyAcquireResult {
    }
}
