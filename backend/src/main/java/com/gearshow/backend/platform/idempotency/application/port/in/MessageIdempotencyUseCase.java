package com.gearshow.backend.platform.idempotency.application.port.in;

import com.gearshow.backend.platform.idempotency.domain.IdempotencyDomain;

/**
 * 메시지 멱등성 처리 유스케이스 (ADR-011 §2.③ Kafka Consumer 계층).
 *
 * <p>Kafka Consumer 등 메시지 핸들러는 이 유스케이스로 같은 메시지의 중복 처리를 차단한다.
 * 처리 권한을 *시작 시점에 선점* 하지 않고, 처리 완료 후 영구 기록하는 방식이다 — ADR-011 의
 * "양보 불가 규칙" 에 따라 시작 시점 INSERT 는 워커 크래시 시 재시도 경로를 막아버리므로 금지된다.</p>
 *
 * <p><b>동시성 1회 보장</b>은 이 테이블이 아니라 워크플로우 단계의 조건부 UPDATE
 * (ADR-012) 가 책임진다. 이 유스케이스는 Kafka 브로커 재전송 / rebalance 로 같은 메시지가 다시
 * 배달될 때 *빠른 컷* 역할만 수행한다.</p>
 */
public interface MessageIdempotencyUseCase {

    /**
     * 메시지가 이미 처리 완료되었는지 조회한다. 부수 효과 없음.
     *
     * @param messageId 메시지 고유 식별자
     * @param domain    멱등성 도메인 (Consumer 별 네임스페이스)
     * @return 이미 처리된 메시지면 {@code true}, 처음 보는 메시지면 {@code false}
     */
    boolean isProcessed(String messageId, IdempotencyDomain domain);

    /**
     * 메시지 처리 완료 사실을 영구 기록한다. 비즈니스 로직 *정상 종료 후* 호출.
     *
     * <p>UNIQUE 충돌은 정상 시나리오 (두 워커가 같은 메시지를 동시 처리한 경우 둘 다 호출 가능)
     * 이므로 예외를 던지지 않고 멱등 무시한다.</p>
     *
     * @param messageId 메시지 고유 식별자
     * @param domain    멱등성 도메인
     */
    void markProcessed(String messageId, IdempotencyDomain domain);
}
