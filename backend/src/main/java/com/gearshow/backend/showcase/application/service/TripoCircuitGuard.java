package com.gearshow.backend.showcase.application.service;

import com.gearshow.backend.showcase.application.exception.TripoUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tripo Circuit Breaker 상태를 사용자 요청 진입 시점에 검사해 사전 거부하는 공용 가드.
 *
 * <p>쇼케이스 최초 등록({@code CreateShowcaseFacade}) 과 3D 재시도 API({@code RequestModelGenerationFacade.requestRetry})
 * 두 진입 경로가 같은 정책을 공유하도록 추출했다. Worker 경로의 {@code CallNotPermittedException}
 * 은 {@code @RetryableTopic} 의 backoff 가 담당하고, 이 가드는 사용자 호출 순간에만 작용한다.</p>
 *
 * <p><b>추상화 연기</b>: {@code CircuitBreakerRegistry} 를 application 계층에서 직접 의존한다.
 * 추후 {@code ExternalServiceHealthPort} 로 추상화할 수 있으며(연기 목록), 이 가드는 그 시점의
 * 단일 교체 지점 역할을 한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripoCircuitGuard {

    /** TripoApiClient 와 동일 키. 단일 출처 유지가 목적. */
    public static final String TRIPO_CIRCUIT = "tripo";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Tripo Circuit 이 {@code OPEN} 또는 {@code FORCED_OPEN} 이면 {@link TripoUnavailableException}(503)
     * 을 던진다. 그 외 상태({@code CLOSED}, {@code HALF_OPEN}, {@code METRICS_ONLY}, {@code DISABLED})
     * 는 통과시킨다 — half-open 시점의 탐색 호출을 허용해 복구를 빠르게 감지한다.
     */
    public void rejectIfOpen() {
        CircuitBreaker.State state = circuitBreakerRegistry.circuitBreaker(TRIPO_CIRCUIT).getState();
        if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
            log.warn("Tripo Circuit {} — 요청 거부. state: {}", TRIPO_CIRCUIT, state);
            throw new TripoUnavailableException();
        }
    }
}
