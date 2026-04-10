package com.gearshow.backend.platform.idempotency.application.port.in;

import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;

/**
 * 멱등성 처리 권한 획득 유스케이스.
 *
 * <p>Kafka Consumer 등 메시지 핸들러는 비즈니스 로직 실행 전 이 유스케이스를 호출하여
 * 같은 메시지가 중복 처리되지 않도록 보장한다.</p>
 */
public interface AcquireIdempotencyUseCase {

    /**
     * 메시지 처리 권한을 획득한다.
     *
     * @param messageId 메시지 고유 식별자
     * @param domain    멱등성 도메인 (Consumer별 네임스페이스)
     * @return 처음 처리되는 메시지면 {@code true}, 이미 처리된 메시지면 {@code false}
     */
    boolean tryAcquire(String messageId, IdempotencyDomain domain);

    /**
     * 처리 권한 선점을 되돌린다.
     *
     * <p>비즈니스 로직 처리 실패 시 호출하여 멱등성 레코드를 삭제하면
     * 다음 메시지 재전달 시 다시 처리할 수 있다.
     * 좀비 메시지(선점만 되고 처리 안 된 상태)를 방지한다.</p>
     *
     * @param messageId 메시지 고유 식별자
     * @param domain    멱등성 도메인
     */
    void release(String messageId, IdempotencyDomain domain);
}
