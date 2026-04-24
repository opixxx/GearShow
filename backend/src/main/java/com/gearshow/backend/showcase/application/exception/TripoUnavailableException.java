package com.gearshow.backend.showcase.application.exception;

import com.gearshow.backend.common.exception.CustomException;
import com.gearshow.backend.common.exception.ErrorCode;

/**
 * 3D 모델 생성 API 호출 시점에 Tripo 서비스의 Circuit Breaker 가 {@code OPEN} 상태여서
 * 사용자 요청을 즉시 거부할 때 던진다 (HTTP 503).
 *
 * <p>Worker 경로에서 이미 큐에 들어간 메시지는 {@code @RetryableTopic} 으로 backoff 재시도하지만,
 * 새로 들어오는 사용자 요청은 Outbox/Kafka 에 쌓아두기보다 이 시점에 미리 거부하는 것이
 * 명확하고 빠르다. Circuit 복구 시 자연스럽게 다시 허용된다.</p>
 */
public class TripoUnavailableException extends CustomException {

    public TripoUnavailableException() {
        super(ErrorCode.TRIPO_UNAVAILABLE);
    }
}
