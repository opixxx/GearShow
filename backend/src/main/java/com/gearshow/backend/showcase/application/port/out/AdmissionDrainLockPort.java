package com.gearshow.backend.showcase.application.port.out;

import java.time.Duration;

/**
 * 입장 큐 drainer 의 멀티 인스턴스 동시 pop 방지용 단명 분산 락 (ADR-025).
 *
 * <p>키 {@code tripo:queue:drain-lock}. 비대기({@code tryLock} 즉시 반환) + lease 자동 해제 —
 * 락 보유 인스턴스가 사망해도 lease 만료로 자동 회수되어 누수가 없다. 락 스코프는
 * {@code countActive + ZSET pop + REQUESTED 확인 + 대상 목록 구성}까지로 한정하며 외부 I/O
 * (Kafka send)는 절대 락 밖에서 한다(NN3·NN10).</p>
 */
public interface AdmissionDrainLockPort {

    /**
     * 락을 비대기로 시도한다.
     *
     * @param lease 자동 해제 시간(보유 인스턴스 사망 대비). drainer 1 tick 처리 상한 + 마진.
     * @return 획득했으면 {@code true}, 다른 인스턴스가 보유 중이면 {@code false}
     */
    boolean tryLock(Duration lease);

    /** 현재 스레드가 보유 중이면 해제한다(lease 선만료 시 no-op). */
    void unlock();
}
