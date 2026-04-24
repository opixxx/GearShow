package com.gearshow.backend.showcase.application.port.out;

import com.gearshow.backend.showcase.application.exception.TripoSemaphoreTimeoutException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Tripo 동시 요청 제한 세마포어 포트 (설계 §3.4 — {@code tripo:semaphore} 10 permits).
 *
 * <p>Worker 의 Tripo upload+POST 와 Poller 의 getTask 호출이 같은 세마포어를 공유해 Tripo 측
 * rate limit 을 넘지 않도록 한다. 획득 실패 시 {@link TripoSemaphoreTimeoutException} 을
 * 던져 호출자가 재시도(Worker: {@code @RetryableTopic}, Poller: 짧은 delay 로 재queue)로 전파한다.</p>
 */
public interface TripoSemaphorePort {

    /**
     * 세마포어 permit 을 획득한 뒤 {@code action} 을 실행하고 해제한다.
     *
     * @param timeout permit 획득 대기 상한 — 초과 시 {@link TripoSemaphoreTimeoutException}
     * @param action  permit 안에서 실행할 작업. 호출 중 발생하는 예외는 그대로 전파.
     * @throws TripoSemaphoreTimeoutException 획득 대기 시간 초과
     */
    <T> T runWithPermit(Duration timeout, Supplier<T> action);
}
